package com.example.consumer;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
class SpringBootKafkaDlqApplicationTests {

	private static final String TOPIC = "dlq-test";

	// Spring Kafka 4.x default DLT suffix is "-dlt"
	private static final String DLT_TOPIC = TOPIC + "-dlt";

	@ServiceConnection
	@Container
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private FailingListener failingListener;

	@Autowired
	private DltListener dltListener;

	@Test
	void messageGoesToDltAfterProcessingFailure() {
		this.kafkaTemplate.send(TOPIC, "bad-message");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.dltListener.messages).hasSize(1);
			assertThat(this.dltListener.messages).containsExactly("bad-message");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		NewTopic dlqTestTopic() {
			return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
		}

		@Bean
		NewTopic dlqTestDltTopic() {
			return TopicBuilder.name(DLT_TOPIC).partitions(1).replicas(1).build();
		}

		@Bean
		ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
				ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
			ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
			factory.setConsumerFactory(consumerFactory);
			factory.setCommonErrorHandler(new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate),
					new FixedBackOff(0L, 0L)));
			return factory;
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

		@KafkaListener(topics = TOPIC, groupId = "dlq-test-group")
		void listen(String data) {
			throw new RuntimeException("Simulated failure for: " + data);
		}

	}

	static class DltListener {

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = DLT_TOPIC, groupId = "dlq-test-dlt-group")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
