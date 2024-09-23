package com.example.springbootredis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringBootRedisApplicationTests {

	@Container
	@ServiceConnection
	private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void consumeMessage() {
		this.redisTemplate.opsForSet().add("test", "test-data");

		assertThat(this.redisTemplate.opsForSet().pop("test")).isEqualTo("test-data");
	}

}
