package com.example.springbootartemis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
class SpringBootArtemisApplicationTests {

	@Container
	static GenericContainer<?> artemis = new GenericContainer<>("apache/activemq-artemis:2.30.0-alpine")
		.withExposedPorts(61616);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.artemis.broker-url",
				() -> "tcp://%s:%d".formatted(artemis.getHost(), artemis.getMappedPort(61616)));
		registry.add("spring.artemis.user", () -> "artemis");
		registry.add("spring.artemis.password", () -> "artemis");
	}

	@Autowired
	private JmsTemplate jmsTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.jmsTemplate.convertAndSend("test", "test-data");

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

		@JmsListener(destination = "test")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
