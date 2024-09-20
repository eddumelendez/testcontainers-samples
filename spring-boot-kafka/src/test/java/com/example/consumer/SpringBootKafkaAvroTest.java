package com.example.consumer;

import com.example.avro.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
		"spring.kafka.producer.value-serializer=io.confluent.kafka.serializers.KafkaAvroSerializer",
		"spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
		"spring.kafka.consumer.value-deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer",
		"spring.kafka.consumer.properties.specific.avro.reader=true" })
@Testcontainers
public class SpringBootKafkaAvroTest {

	static Network network = Network.newNetwork();

	@Container
	@ServiceConnection
	static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
		.withNetwork(network)
		.withListener(() -> "kafka:19092");

	@Container
	static GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:7.4.0")
		.dependsOn(kafka)
		.withExposedPorts(8085)
		.withNetworkAliases("schemaregistry")
		.withNetwork(network)
		.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
		.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8085")
		.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schemaregistry")
		.withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
		.waitingFor(Wait.forHttp("/subjects"))
		.withStartupTimeout(Duration.ofSeconds(120));

	@Autowired
	private KafkaTemplate<String, Message> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send("test", Message.newBuilder().setText("test").build());

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.testListener.messages.getFirst().getText()).isEqualTo("test");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		DefaultKafkaProducerFactoryCustomizer kafkaProducerFactoryCustomizer() {
			return factory -> {
				Map<String, Object> props = new HashMap<>();
				props.put("schema.registry.url",
						"http://%s:%d".formatted(schemaRegistry.getHost(), schemaRegistry.getMappedPort(8085)));
				factory.updateConfigs(props);
			};
		}

		@Bean
		DefaultKafkaConsumerFactoryCustomizer kafkaConsumerFactoryCustomizer() {
			return factory -> {
				Map<String, Object> props = new HashMap<>();
				props.put("schema.registry.url",
						"http://%s:%d".formatted(schemaRegistry.getHost(), schemaRegistry.getMappedPort(8085)));
				factory.updateConfigs(props);
			};
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener {

		private final List<Message> messages = new ArrayList<>();

		@KafkaListener(topics = "test", groupId = "test")
		void listen(Message data) {
			this.messages.add(data);
		}

	}

}
