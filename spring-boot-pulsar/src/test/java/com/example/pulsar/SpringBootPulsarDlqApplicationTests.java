package com.example.pulsar;

import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.testcontainers.pulsar.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
class SpringBootPulsarDlqApplicationTests {

	private static final String TOPIC = "dlq-test";

	private static final String DLT_TOPIC = "dlq-test-dlt";

	@Container
	@ServiceConnection
	static final PulsarContainer pulsar = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.1.0"));

	@Autowired
	private PulsarTemplate<String> pulsarTemplate;

	@Autowired
	private FailingListener failingListener;

	@Autowired
	private DltListener dltListener;

	@Test
	void messageGoesToDltAfterProcessingFailure() {
		this.pulsarTemplate.send(TOPIC, "bad-message");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.dltListener.messages).hasSize(1);
			assertThat(this.dltListener.messages).containsExactly("bad-message");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		DeadLetterPolicy deadLetterPolicy() {
			return DeadLetterPolicy.builder().maxRedeliverCount(1).deadLetterTopic(DLT_TOPIC).build();
		}

		@Bean
		RedeliveryBackoff redeliveryBackoff() {
			return MultiplierRedeliveryBackoff.builder().minDelayMs(100).maxDelayMs(1000).multiplier(2).build();
		}

		@Bean
		FailingListener failingListener() {
			return new FailingListener();
		}

		@Bean
		DltListener dltListener() {
			return new DltListener();
		}

	}

	static class FailingListener {

		@PulsarListener(topics = TOPIC, subscriptionName = "dlq-subscription",
				subscriptionType = SubscriptionType.Shared, deadLetterPolicy = "deadLetterPolicy",
				negativeAckRedeliveryBackoff = "redeliveryBackoff")
		void listen(String data) {
			throw new RuntimeException("Simulated failure for: " + data);
		}

	}

	static class DltListener {

		private final List<String> messages = new ArrayList<>();

		@PulsarListener(topics = DLT_TOPIC, subscriptionName = "dlt-subscription")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
