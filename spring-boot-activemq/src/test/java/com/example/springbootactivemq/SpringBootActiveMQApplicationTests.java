package com.example.springbootactivemq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
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
class SpringBootActiveMQApplicationTests {

	@Container
	static GenericContainer<?> activemq = new GenericContainer("symptoma/activemq:5.18.0").withExposedPorts(61616);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.activemq.broker-url",
				() -> "tcp://%s:%d".formatted(activemq.getHost(), activemq.getMappedPort(61616)));
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