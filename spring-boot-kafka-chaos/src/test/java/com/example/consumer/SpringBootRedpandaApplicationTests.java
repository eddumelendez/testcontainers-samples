package com.example.consumer;

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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
class SpringBootRedpandaApplicationTests {

	static Network network = Network.newNetwork();

	@Container
	static ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0")
		.withNetwork(network);

	@Container
	static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v23.2.1")
		.withListener("redpanda:19092",
				() -> String.format("%s:%s", toxiproxy.getHost(), toxiproxy.getMappedPort(8666)))
		.withNetwork(network)
		.dependsOn(toxiproxy);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws Exception {
		execute("./toxiproxy-cli create -l 0.0.0.0:8666 -u redpanda:19092 redpanda");

		registry.add("spring.kafka.bootstrap-servers",
				() -> "%s:%d".formatted(toxiproxy.getHost(), toxiproxy.getMappedPort(8666)));
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

		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream redpanda");

		waitAtMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
			System.out.println("Ping");
			assertThat(this.testListener.messages).hasSize(1);
		});

		execute("./toxiproxy-cli toxic remove -n latency_downstream redpanda");
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