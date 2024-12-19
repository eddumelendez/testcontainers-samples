package com.example.consumer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.kafka.streams.application-id=test-stream",
		"spring.kafka.admin.auto-create=false", "spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.streams.properties.default.key.serde=org.apache.kafka.common.serialization.Serdes$StringSerde",
		"spring.kafka.streams.properties.default.value.serde=org.apache.kafka.common.serialization.Serdes$StringSerde" })
@Testcontainers
@ExtendWith(SpringBootKafkaStreamsApplicationTests.KafkaStreamsAfterAllCallback.class)
class SpringBootKafkaStreamsApplicationTests {

	private static final String INPUT_TOPIC = "input-topic";

	private static final String OUTPUT_TOPIC = "output-topic";

	@Container
	@ServiceConnection
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@BeforeAll
	static void setupAll() {
		try (AdminClient adminClient = AdminClient
			.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
			adminClient
				.createTopics(List.of(TopicBuilder.name(INPUT_TOPIC).build(), TopicBuilder.name(OUTPUT_TOPIC).build()));
		}
	}

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send(INPUT_TOPIC, "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.testListener.messages).containsExactly("TEST-DATA");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

		@Bean
		KStream<String, String> kStream(StreamsBuilder streamsBuilder) {
			KStream<String, String> stream = streamsBuilder.stream(INPUT_TOPIC);

			stream.mapValues(value -> value.toUpperCase()).to(OUTPUT_TOPIC);

			return stream;
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = OUTPUT_TOPIC, groupId = "test")
		void listen(String data) {
			this.messages.add(data);
		}

	}

	static class KafkaStreamsAfterAllCallback implements AfterAllCallback {

		@Override
		public void afterAll(ExtensionContext extensionContext) {
			ApplicationContext applicationContext = SpringExtension.getApplicationContext(extensionContext);
			StreamsBuilderFactoryBean streamsBuilderFactoryBean = applicationContext
				.getBean(StreamsBuilderFactoryBean.class);
			streamsBuilderFactoryBean.stop();
		}

	}

}
