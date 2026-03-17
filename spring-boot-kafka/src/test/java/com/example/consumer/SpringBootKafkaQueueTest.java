package com.example.consumer;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ShareKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultShareConsumerFactory;
import org.springframework.kafka.core.ShareConsumerFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
class SpringBootKafkaQueueTest {

	private static final String TOPIC = "queue-test";

	private static final String SHARE_GROUP = "my-share-group";

	@ServiceConnection
	@Container
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0")
		.withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
		.withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private ShareTestListener shareTestListener;

	@BeforeAll
	static void setupAll() throws Exception {
		Map<String, Object> adminProps = new HashMap<>();
		adminProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

		try (Admin admin = Admin.create(adminProps)) {
			ConfigResource configResource = new ConfigResource(ConfigResource.Type.GROUP, SHARE_GROUP);
			ConfigEntry configEntry = new ConfigEntry("share.auto.offset.reset", "earliest");

			Map<ConfigResource, Collection<AlterConfigOp>> configs = Map.of(configResource,
					List.of(new AlterConfigOp(configEntry, AlterConfigOp.OpType.SET)));

			admin.incrementalAlterConfigs(configs).all().get();
		}
	}

	@Test
	void consumeMessageUsingShareConsumer() {
		this.kafkaTemplate.send(TOPIC, "queue-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.shareTestListener.messages).hasSize(1);
			assertThat(this.shareTestListener.messages).containsExactly("queue-data");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		ShareConsumerFactory<String, String> shareConsumerFactory() {
			Map<String, Object> properties = new HashMap<>();
			properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
			properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			return new DefaultShareConsumerFactory<>(properties);
		}

		@Bean
		ShareKafkaListenerContainerFactory<String, String> shareKafkaListenerContainerFactory(
				ShareConsumerFactory<String, String> shareConsumerFactory) {
			return new ShareKafkaListenerContainerFactory<>(shareConsumerFactory);
		}

		@Bean
		ShareTestListener shareTestListener() {
			return new ShareTestListener();
		}

	}

	static class ShareTestListener {

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = TOPIC, containerFactory = "shareKafkaListenerContainerFactory", groupId = SHARE_GROUP)
		void listen(ConsumerRecord<String, String> record) {
			this.messages.add(record.value());
		}

	}

}
