package com.example.consumer;

import com.rabbitmq.stream.Address;
import com.rabbitmq.stream.Environment;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.EnvironmentBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.support.StreamAdmin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.rabbitmq.stream.name=stream.queue1", "spring.rabbitmq.listener.type=stream" })
@Testcontainers
class SpringBootRabbitMQStreamsApplicationTests {

	static final int RABBITMQ_STREAMS_PORT = 5552;

	@Container
	static RabbitMQContainer rabbitmq = new RabbitMqStreamContainer();

	@Autowired
	private RabbitStreamTemplate rabbitStreamTemplate;

	@Autowired
	private TestListener testListener;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.stream.host", rabbitmq::getHost);
		registry.add("spring.rabbitmq.stream.port", () -> rabbitmq.getMappedPort(RABBITMQ_STREAMS_PORT));
	}

	@Test
	void consumeMessage() {
		this.rabbitStreamTemplate.convertAndSend("test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
			assertThat(this.testListener.messages.getFirst()).isEqualTo("test-data");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		StreamAdmin streamAdmin(Environment env) {
			return new StreamAdmin(env, sc -> {
				sc.stream("stream.queue1").create();
			});
		}

		@Bean
		EnvironmentBuilderCustomizer environmentBuilderCustomizer() {
			return env -> {
				Address entrypoint = new Address(rabbitmq.getHost(), rabbitmq.getMappedPort(RABBITMQ_STREAMS_PORT));
				env.addressResolver(address -> entrypoint);
			};
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@RabbitListener(queues = "stream.queue1")
		void listen(String data) {
			this.messages.add(data);
		}

	}

	static class RabbitMqStreamContainer extends RabbitMQContainer {

		public RabbitMqStreamContainer() {
			super("rabbitmq:4.0.0-alpine");
			addExposedPorts(RABBITMQ_STREAMS_PORT);
			var enabledPlugins = "[rabbitmq_stream,rabbitmq_prometheus].";
			withCopyToContainer(Transferable.of(enabledPlugins), "/etc/rabbitmq/enabled_plugins");
		}

	}

}
