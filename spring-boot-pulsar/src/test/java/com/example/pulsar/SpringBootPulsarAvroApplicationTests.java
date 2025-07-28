package com.example.pulsar;

import com.example.avro.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.pulsar.defaults.type-mappings[0].message-type=com.example.avro.Message",
		"spring.pulsar.defaults.type-mappings[0].topic-name=test",
		"spring.pulsar.defaults.type-mappings[0].schema-info.schema-type=AVRO" })
class SpringBootPulsarAvroApplicationTests {

	@Container
	@ServiceConnection
	static final PulsarContainer pulsar = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.1.0"));

	@Autowired
	private PulsarTemplate<Message> pulsarTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.pulsarTemplate.send(new Message("test"));

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

		@PulsarListener
		void listen(Message data) {
			this.messages.add(data);
		}

	}

}
