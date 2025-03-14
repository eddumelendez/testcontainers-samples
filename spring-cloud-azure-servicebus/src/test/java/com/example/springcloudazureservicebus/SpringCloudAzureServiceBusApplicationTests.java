package com.example.springcloudazureservicebus;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.spring.cloud.service.servicebus.consumer.ServiceBusErrorHandler;
import com.azure.spring.cloud.service.servicebus.consumer.ServiceBusRecordMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest(properties = { "spring.cloud.azure.servicebus.entity-name=queue.1",
		"spring.cloud.azure.servicebus.entity-type=queue" })
@Testcontainers
class SpringCloudAzureServiceBusApplicationTests {

	private static final Network network = Network.newNetwork();

	private static MSSQLServerContainer<?> sqlserver = new MSSQLServerContainer<>(
			"mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04")
		.acceptLicense()
		.withNetwork(network)
		.withNetworkAliases("sqlserver");

	@Container
	private static final ServiceBusEmulatorContainer serviceBus = new ServiceBusEmulatorContainer(
			"mcr.microsoft.com/azure-messaging/servicebus-emulator:latest")
		.withConfig(MountableFile.forClasspathResource("Config.json"))
		.withNetwork(network)
		.acceptLicense()
		.withEnv("SQL_WAIT_INTERVAL", "0")
		.withMsSqlServerContainer(sqlserver);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.azure.servicebus.connection-string", serviceBus::getConnectionString);
	}

	@Autowired
	private ServiceBusSenderClient senderClient;

	@Test
	void contextLoads() {
		this.senderClient.sendMessage(new ServiceBusMessage("testcontainers"));

		waitAtMost(Duration.ofSeconds(30)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(Config.messages).hasSize(1);
			assertThat(Config.messages.getFirst().getBody().toString()).isEqualTo("testcontainers");
		});
	}

	@TestConfiguration
	static class Config {

		static final List<ServiceBusReceivedMessage> messages = new ArrayList<>();

		@Bean
		ServiceBusRecordMessageListener processMessage() {
			return context -> {
				messages.add(context.getMessage());
			};
		}

		@Bean
		ServiceBusErrorHandler errorHandler() {
			return (context) -> {
				throw new RuntimeException("Error processing message: " + context.getException().getMessage());
			};
		}

	}

}