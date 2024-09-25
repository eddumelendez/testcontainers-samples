package com.example.consumer;

import com.example.avro.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
		"spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteBufferSerializer",
		"spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
		"spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteBufferDeserializer" })
@Testcontainers
public class SpringBootKafkaAvroByteBufferTest {

	@Container
	@ServiceConnection
	static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

	@Autowired
	private KafkaTemplate<ByteBuffer, ByteBuffer> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() throws IOException {
		this.kafkaTemplate.send("test", Message.newBuilder().setText("test").build().toByteBuffer());

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.testListener.messages.getFirst().getText()).isEqualTo("test");
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

		private final List<Message> messages = new ArrayList<>();

		@KafkaListener(topics = "test", groupId = "test")
		void listen(ByteBuffer data) throws IOException {
			this.messages.add(Message.fromByteBuffer(data));
		}

	}

}
