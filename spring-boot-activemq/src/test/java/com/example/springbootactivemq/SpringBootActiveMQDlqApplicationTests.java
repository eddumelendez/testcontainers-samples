package com.example.springbootactivemq;

import org.apache.activemq.RedeliveryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionFactoryCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsClient;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
class SpringBootActiveMQDlqApplicationTests {

	@Container
	@ServiceConnection
	static final ActiveMQContainer activeMQ = new ActiveMQContainer("apache/activemq:6.2.2");

	@Autowired
	private JmsClient jmsClient;

	@Autowired
	private FailingListener failingListener;

	@Autowired
	private DlqListener dlqListener;

	@Test
	void messageGoesToDlqAfterProcessingFailure() {
		this.jmsClient.destination("dlq-test").send("bad-message");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.dlqListener.messages).hasSize(1);
			assertThat(this.dlqListener.messages).containsExactly("bad-message");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		ActiveMQConnectionFactoryCustomizer redeliveryCustomizer() {
			return factory -> {
				RedeliveryPolicy policy = new RedeliveryPolicy();
				policy.setMaximumRedeliveries(1);
				policy.setInitialRedeliveryDelay(0L);
				factory.setRedeliveryPolicy(policy);
			};
		}

		@Bean
		FailingListener failingListener() {
			return new FailingListener();
		}

		@Bean
		DlqListener dlqListener() {
			return new DlqListener();
		}

	}

	static class FailingListener {

		@JmsListener(destination = "dlq-test")
		void listen(String data) {
			throw new RuntimeException("Simulated failure for: " + data);
		}

	}

	static class DlqListener {

		private final List<String> messages = new ArrayList<>();

		@JmsListener(destination = "ActiveMQ.DLQ")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
