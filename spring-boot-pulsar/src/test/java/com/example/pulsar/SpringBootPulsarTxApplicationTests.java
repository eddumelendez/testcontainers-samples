package com.example.pulsar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.pulsar.listener.AckMode;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = "spring.pulsar.transaction.enabled=true")
class SpringBootPulsarTxApplicationTests {

	@Autowired
	private PulsarTemplate<String> pulsarTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
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

		@PulsarListener(topics = "test", ackMode = AckMode.RECORD)
		void listen(String data) {
			this.messages.add(data);
		}

	}

	@TestConfiguration
	static class TestcontainersConfiguration {

		@Bean
		@ServiceConnection
		PulsarContainer pulsar() {
			return new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.1.0")).withTransactions();
		}

	}

}
