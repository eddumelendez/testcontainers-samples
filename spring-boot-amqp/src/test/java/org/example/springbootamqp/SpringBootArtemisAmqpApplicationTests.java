package org.example.springbootamqp;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.amqp.client.AmqpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringBootArtemisAmqpApplicationTests {

	private static final String address = UUID.randomUUID().toString();

	@Container
	@ServiceConnection
	private static final ArtemisContainer artemis = new ArtemisContainer("apache/artemis:2.53.0-alpine")
		.withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --queues %s:anycast".formatted(address));

	@Autowired
	private AmqpClient amqpClient;

	@Test
	void contextLoads() throws ExecutionException, InterruptedException, TimeoutException {
		this.amqpClient.to(address).body("test").send();
		Object message = this.amqpClient.from(address).receiveAndConvert().get(1, TimeUnit.MINUTES);
		assertThat(message).isEqualTo("test");
	}

}
