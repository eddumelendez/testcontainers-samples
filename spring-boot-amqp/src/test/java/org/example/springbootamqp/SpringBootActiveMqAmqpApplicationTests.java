package org.example.springbootamqp;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.amqp.client.AmqpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringBootActiveMqAmqpApplicationTests {

	@Container
	@ServiceConnection
	private static final ActiveMQContainer activemq = new ActiveMQContainer("apache/activemq:6.2.2");

	@Autowired
	private AmqpClient amqpClient;

	@Test
	void contextLoads() throws ExecutionException, InterruptedException, TimeoutException {
		String address = UUID.randomUUID().toString();
		this.amqpClient.to(address).body("test").send();
		Object message = this.amqpClient.from(address).receiveAndConvert().get(1, TimeUnit.MINUTES);
		assertThat(message).isEqualTo("test");
	}

}
