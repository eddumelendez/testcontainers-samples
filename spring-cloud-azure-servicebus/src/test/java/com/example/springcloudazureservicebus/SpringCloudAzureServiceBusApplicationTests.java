package com.example.springcloudazureservicebus;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.spring.cloud.service.servicebus.consumer.ServiceBusErrorHandler;
import com.azure.spring.cloud.service.servicebus.consumer.ServiceBusRecordMessageListener;
import com.github.dockerjava.api.model.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
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

	private static final int AZURE_SERVICEBUS_PORT = 5672;

	private static final GenericContainer<?> azureSqlEdge = new GenericContainer<>(
			"mcr.microsoft.com/azure-sql-edge:latest")
		.withExposedPorts(1433)
		.withNetwork(network)
		.withNetworkAliases("sqledge")
		.withEnv("ACCEPT_EULA", "Y")
		.withEnv("MSSQL_SA_PASSWORD", "yourStrong(!)Password")
		.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCapAdd(Capability.SYS_PTRACE));

	@Container
	private static final GenericContainer<?> serviceBus = new GenericContainer<>(
			"mcr.microsoft.com/azure-messaging/servicebus-emulator:latest")
		.withCopyFileToContainer(MountableFile.forClasspathResource("Config.json"),
				"/ServiceBus_Emulator/ConfigFiles/Config.json")
		.withExposedPorts(AZURE_SERVICEBUS_PORT)
		.waitingFor(Wait.forLogMessage(".*Emulator Service is Successfully Up!.*", 1))
		.withNetwork(network)
		.withEnv("SQL_SERVER", "sqledge")
		.withEnv("MSSQL_SA_PASSWORD", "yourStrong(!)Password")
		.withEnv("ACCEPT_EULA", "Y")
		.dependsOn(azureSqlEdge);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		var serviceBusHost = serviceBus.getHost();
		var serviceBusPort = serviceBus.getMappedPort(AZURE_SERVICEBUS_PORT);
		var connectionString = "Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;"
			.formatted(serviceBusHost, serviceBusPort);
		registry.add("spring.cloud.azure.servicebus.connection-string", () -> connectionString);
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