package com.example.pulsar;

import com.example.protobuf.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(
		properties = { "spring.pulsar.defaults.type-mappings[0].message-type=com.example.protobuf.Message.MyMessage",
				"spring.pulsar.defaults.type-mappings[0].topic-name=test",
				"spring.pulsar.defaults.type-mappings[0].schema-info.schema-type=PROTOBUF" })
class SpringBootPulsarProtoBufApplicationTests {

	@Autowired
	private PulsarTemplate<Message.MyMessage> pulsarTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.pulsarTemplate.send(Message.MyMessage.newBuilder().setText("test").build());

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

		private final List<Message.MyMessage> messages = new ArrayList<>();

		@PulsarListener
		void listen(Message.MyMessage data) {
			this.messages.add(data);
		}

	}

	@TestConfiguration
	static class TestcontainersConfiguration {

		@Bean
		@ServiceConnection
		PulsarContainer pulsar() {
			return new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.1.0"));
		}

	}

}
