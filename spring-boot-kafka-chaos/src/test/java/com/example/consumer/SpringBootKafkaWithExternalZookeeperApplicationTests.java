package com.example.consumer;

import com.example.container.ToxicKafkaWithExternalZookeeperContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class SpringBootKafkaWithExternalZookeeperApplicationTests {

	static Network network = Network.newNetwork();

	@Container
	static ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.8.0")
		.withNetwork(network);

	@Container
	static GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.8.0").withExposedPorts(2181)
		.withNetwork(network)
		.withNetworkAliases("zookeeper");

	@Container
	static KafkaContainer kafka = new ToxicKafkaWithExternalZookeeperContainer("confluentinc/cp-kafka:7.4.0")
		.withAdditionalListener(() -> String.format("%s:%s", toxiproxy.getHost(), toxiproxy.getMappedPort(8666)))
		.withExternalZookeeper("zookeeper:2181")
		.withNetwork(network)
		.withNetworkAliases("kafka")
		.dependsOn(toxiproxy, zookeeper)
		.withEnv("KAFKA_SOCKET_CONNECTION_SETUP_TIMEOUT_MS", "1000")
		.withEnv("KAFKA_SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS", "5000");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws Exception {
		execute("./toxiproxy-cli create -l 0.0.0.0:8666 -u kafka:19092 kafka");

		registry.add("spring.kafka.bootstrap-servers",
				() -> "PLAINTEXT://%s:%d".formatted(toxiproxy.getHost(), toxiproxy.getMappedPort(8666)));
	}

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@BeforeEach
	void setUp() {
		this.testListener.messages.clear();
	}

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
		});
	}

	@Test
	void consumeMessageWithLatency() throws Exception {
		this.kafkaTemplate.send("test", "test-data");

		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream kafka");
		waitAtMost(Duration.ofSeconds(5)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
			System.out.println("Ping");
			assertThat(this.testListener.messages).hasSize(1);
		});

		execute("./toxiproxy-cli toxic remove -n latency_downstream kafka");
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

	private static void execute(String command) throws Exception {
		org.testcontainers.containers.Container.ExecResult result = toxiproxy.execInContainer(command.split(" "));
		if (result.getExitCode() != 0) {
			throw new RuntimeException("Error executing command '%s' \nstderr: %s\nstdout: %s".formatted(command,
					result.getStderr(), result.getStdout()));
		}
	}

}
