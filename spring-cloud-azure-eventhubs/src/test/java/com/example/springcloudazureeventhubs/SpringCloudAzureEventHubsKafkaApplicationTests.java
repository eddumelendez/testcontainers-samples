package com.example.springcloudazureeventhubs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.azure.EventHubsEmulatorContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
@SpringBootTest(properties = { "spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.consumer.enable-auto-commit=true",
		"spring.kafka.security.protocol=SASL_PLAINTEXT",
		"spring.kafka.properties.sasl.mechanism=PLAIN" })
@Testcontainers
class SpringCloudAzureEventHubsKafkaApplicationTests {

	private static final Network network = Network.newNetwork();

	@Container
	private static final AzuriteContainer azurite = new AzuriteContainer(
			"mcr.microsoft.com/azure-storage/azurite:latest")
		.withNetwork(network);

	@Container
	private static final EventHubsEmulatorContainer eventHubs = new EventHubsEmulatorContainer(
			"mcr.microsoft.com/azure-messaging/eventhubs-emulator:latest")
		.withExposedPorts(5672, 9092)
		.withConfig(MountableFile.forClasspathResource("Config.json"))
		.withNetwork(network)
		.acceptLicense()
		.withAzuriteContainer(azurite);

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.properties.sasl.jaas.config", () -> "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"%s\";".formatted(eventHubs.getConnectionString()));
		registry.add("spring.kafka.bootstrap-servers", () -> "%s:%d".formatted(eventHubs.getHost(), eventHubs.getMappedPort(9092)));
//		registry.add("spring.kafka.properties.sasl.jaas.config", () -> "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;\";");
//		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
	}

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send("eh1", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
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

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = "eh1", groupId = "$default")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}