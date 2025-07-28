package com.example.pulsar;

import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
class SpringBootPulsarQueueApplicationTests {

	@Container
	@ServiceConnection
	static final PulsarContainer pulsar = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.1.0"));

	@Autowired
	private PulsarTemplate<String> pulsarTemplate;

	@Autowired
	private TestListener testListener;

	@Autowired
	private AnotherTestListener anotherTestListener;

	@Test
	void consumeMessage() {
		this.pulsarTemplate.send("test", "test-data");
		this.pulsarTemplate.send("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.anotherTestListener.messages).hasSize(1);
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

		@Bean
		AnotherTestListener anotherTestListener() {
			return new AnotherTestListener();
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@PulsarListener(topics = "test", subscriptionName = "first-subscription",
				subscriptionType = SubscriptionType.Shared)
		void listen(String data) {
			this.messages.add(data);
		}

	}

	static class AnotherTestListener {

		private final List<String> messages = new ArrayList<>();

		@PulsarListener(topics = "test", subscriptionName = "first-subscription",
				subscriptionType = SubscriptionType.Shared)
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
