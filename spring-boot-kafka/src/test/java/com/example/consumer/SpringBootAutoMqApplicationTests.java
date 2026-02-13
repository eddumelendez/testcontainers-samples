package com.example.consumer;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
public class SpringBootAutoMqApplicationTests {

	static Network network = Network.newNetwork();

	static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.13.1") {
		@Override
		protected void containerIsStarted(InspectContainerResponse containerInfo) {
			try {
				execInContainer("awslocal s3api create-bucket --bucket ko3".split(" "));
			}
			catch (IOException | InterruptedException e) {
				throw new RuntimeException("Exception creating bucket", e);
			}
		}
	}.withNetwork(network);

	static AutoMqControllerContainer automqController = new AutoMqControllerContainer().withNetwork(network)
		.withNetworkAliases("controller")
		.dependsOn(localstack);

	@Container
	static AutoMqBrokerContainer autoMqBroker = new AutoMqBrokerContainer().withNetwork(network)
		.dependsOn(automqController);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", autoMqBroker::getBootstrapServer);
	}

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.testListener.messages).contains("test-data");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = "test", groupId = "test")
		void listen(String data) {
			this.messages.add(data);
		}

	}

	static class AutoMqControllerContainer extends GenericContainer<AutoMqControllerContainer> {

		public AutoMqControllerContainer() {
			super(DockerImageName.parse("automqinc/automq:1.3.1"));
			withEnv("KAFKA_S3_ACCESS_KEY", "test");
			withEnv("KAFKA_S3_SECRET_KEY", "test");
			withEnv("KAFKA_HEAP_OPTS", "-Xms1g -Xmx1g -XX:MetaspaceSize=96m");
			waitingFor(Wait.forLogMessage(".*SLF4J:.*", 1));
		}

		@Override
		protected void configure() {
			var lsIp = localstack.getContainerInfo()
				.getNetworkSettings()
				.getNetworks()
				.values()
				.iterator()
				.next()
				.getIpAddress();

			withCommand("bash", "-c",
					"/opt/automq/scripts/start.sh up --process.roles controller --node.id 0 --controller.quorum.voters 0@controller:9093 --s3.bucket ko3 --s3.endpoint http://%s:4566 --s3.region us-east-1"
						.formatted(lsIp));
		}

	}

	static class AutoMqBrokerContainer extends GenericContainer<AutoMqBrokerContainer> {

		private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

		public AutoMqBrokerContainer() {
			super(DockerImageName.parse("automqinc/automq:1.3.1"));
			withExposedPorts(9094);
			withNetworkAliases("broker1");
			withEnv("KAFKA_S3_ACCESS_KEY", "test");
			withEnv("KAFKA_S3_SECRET_KEY", "test");
			withEnv("KAFKA_HEAP_OPTS", "-Xms1g -Xmx1g -XX:MetaspaceSize=96m -XX:MaxDirectMemorySize=1G");
			withEnv("KAFKA_CFG_AUTOBALANCER_REPORTER_NETWORK_IN_CAPACITY", "5120");
			withEnv("KAFKA_CFG_AUTOBALANCER_REPORTER_NETWORK_OUT_CAPACITY", "5120");
			withEnv("KAFKA_CFG_AUTOBALANCER_REPORTER_METRICS_REPORTING_INTERVAL_MS", "5000");
			withCommand("bash", "-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
			waitingFor(Wait.forLogMessage(".*SLF4J:.*", 1));
		}

		@Override
		protected void containerIsStarting(InspectContainerResponse containerInfo) {
			var defaultListeners = "PLAINTEXT://:9092,EXTERNAL://:9094";
			var defaultAdvertisedListeners = "PLAINTEXT://localhost:9092,EXTERNAL://%s:%s".formatted(getHost(),
					getMappedPort(9094));
			var defaultSecurityProtocolMap = "CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT,PLAINTEXT:PLAINTEXT";

			var lsIp = localstack.getContainerInfo()
				.getNetworkSettings()
				.getNetworks()
				.values()
				.iterator()
				.next()
				.getIpAddress();

			var script = """
					#!/bin/bash
					export KAFKA_CFG_LISTENERS=%s
					export KAFKA_CFG_ADVERTISED_LISTENERS=%s
					export KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=%s

					/opt/automq/scripts/start.sh up --process.roles broker --node.id 1 --controller.quorum.voters 0@controller:9093 --s3.bucket ko3 --s3.endpoint http://%s:4566 --s3.region us-east-1
					"""
				.formatted(defaultListeners, defaultAdvertisedListeners, defaultSecurityProtocolMap, lsIp);
			copyFileToContainer(Transferable.of(script, 0777), STARTER_SCRIPT);
		}

		public String getBootstrapServer() {
			return "%s:%d".formatted(getHost(), getMappedPort(9094));
		}

	}

}
