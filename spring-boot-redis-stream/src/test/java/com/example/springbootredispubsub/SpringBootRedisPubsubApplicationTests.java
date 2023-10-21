package com.example.springbootredispubsub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
class SpringBootRedisPubsubApplicationTests {

	@Container
	@ServiceConnection
	private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		var uuid = UUID.randomUUID().toString();
		var record = StreamRecords.string(Map.of("id", uuid)).withStreamKey("test");
		var streamOperations = this.redisTemplate.opsForStream();
		streamOperations.add(record);

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
		});
	}

	@TestConfiguration
	static class Config {

		@Bean(initMethod = "start")
		StreamMessageListenerContainer<String, MapRecord<String, String, String>> redisMessageListenerContainer(
				RedisConnectionFactory connectionFactory) {
			var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
				.pollTimeout(Duration.ofMillis(100))
				.build();
			return StreamMessageListenerContainer.create(connectionFactory, options);
		}

		@Bean
		Subscription subscription(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
				TestListener testListener) {
			return container.receive(StreamOffset.fromStart("test"), testListener);
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener implements StreamListener<String, MapRecord<String, String, String>> {

		private final List<String> messages = new ArrayList<>();

		@Override
		public void onMessage(MapRecord<String, String, String> message) {
			this.messages.add(message.getValue().get("id"));
		}

	}

}
