package com.example.pulsar;

import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
class SpringBootPulsarApplicationTests {

	@Container
	static PulsarContainer pulsar = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:2.11.0"));

	@DynamicPropertySource
	static void pulsarProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.pulsar.client.service-url", pulsar::getPulsarBrokerUrl);
	}

	@Autowired
	private PulsarTemplate<String> pulsarTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() throws PulsarClientException {
		this.pulsarTemplate.send("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
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

		@PulsarListener(topics = "test")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
