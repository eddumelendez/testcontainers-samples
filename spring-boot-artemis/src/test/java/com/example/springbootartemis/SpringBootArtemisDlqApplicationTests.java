package com.example.springbootartemis;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.artemis.mode=embedded", "spring.artemis.embedded.persistent=false" })
class SpringBootArtemisDlqApplicationTests {

	@Autowired
	private JmsClient jmsClient;

	@Autowired
	private FailingListener failingListener;

	@Autowired
	private DlqListener dlqListener;

	@Test
	void messageGoesToDlqAfterProcessingFailure() {
		this.jmsClient.destination("dlq-test").send("bad-message");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.dlqListener.messages).hasSize(1);
			assertThat(this.dlqListener.messages).containsExactly("bad-message");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		ArtemisConfigurationCustomizer artemisConfigurationCustomizer() {
			return config -> config.addAddressSetting("#",
					new AddressSettings().setDeadLetterAddress(SimpleString.of("DLQ")).setMaxDeliveryAttempts(2));
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

		@JmsListener(destination = "dlq-test")
		void listen(String data) {
			throw new RuntimeException("Simulated failure for: " + data);
		}

	}

	static class DlqListener {

		private final List<String> messages = new ArrayList<>();

		@JmsListener(destination = "DLQ")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
