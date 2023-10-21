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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
		this.redisTemplate.convertAndSend("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
				MessageListener messageListener) {
			RedisMessageListenerContainer container = new RedisMessageListenerContainer();
			container.setConnectionFactory(connectionFactory);
			container.addMessageListener(messageListener, new ChannelTopic("test"));
			return container;
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener implements MessageListener {

		private final List<String> messages = new ArrayList<>();

		@Override
		public void onMessage(Message message, byte[] pattern) {
			this.messages.add(new String(message.getBody()));
		}

	}

}
