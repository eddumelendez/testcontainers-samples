package com.example.springbootartemis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsClient;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
class SpringBootArtemisApplicationTests {

	@Container
	@ServiceConnection
	static final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:2.32.0-alpine");

	@Autowired
	private JmsClient jmsClient;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.jmsClient.destination("test").send("test-data");

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
