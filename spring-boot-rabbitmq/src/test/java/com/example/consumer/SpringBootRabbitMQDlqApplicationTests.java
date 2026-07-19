package com.example.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
class SpringBootRabbitMQDlqApplicationTests {

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.0.0-alpine");

	@Autowired
	private AmqpTemplate amqpTemplate;

	@Autowired
	private FailingListener failingListener;

	@Autowired
	private DlqListener dlqListener;

	@Test
	void messageGoesToDlqAfterProcessingFailure() {
		this.amqpTemplate.convertAndSend("dlq-test", "bad-message");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.dlqListener.messages).hasSize(1);
			assertThat(this.dlqListener.messages).containsExactly("bad-message");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		Queue mainQueue() {
			return QueueBuilder.durable("dlq-test").withArgument("x-dead-letter-exchange", "dlq-exchange").build();
		}

		@Bean
		DirectExchange deadLetterExchange() {
			return new DirectExchange("dlq-exchange");
		}

		@Bean
		Queue deadLetterQueue() {
			return QueueBuilder.durable("dlq-test.dlq").build();
		}

		@Bean
		Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
			return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("dlq-test");
		}

		@Bean
		SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
			SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
			factory.setConnectionFactory(connectionFactory);
			factory.setDefaultRequeueRejected(false);
			return factory;
		}

		@Bean
		FailingListener failingListener() {
			return new FailingListener();
		}

		@Bean
		DlqListener dlqListener() {
			return new DlqListener();
		}

	}

	static class FailingListener {

		@RabbitListener(queues = "dlq-test")
		void listen(String data) {
			throw new RuntimeException("Simulated failure for: " + data);
		}

	}

	static class DlqListener {

		private final List<String> messages = new ArrayList<>();

		@RabbitListener(queues = "dlq-test.dlq")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
