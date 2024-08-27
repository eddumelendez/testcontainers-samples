package com.example.springcloudazureeventhubs;

import com.azure.core.util.IterableStream;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubConsumerClient;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.PartitionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
@SpringBootTest(properties = { "spring.cloud.azure.eventhubs.event-hub-name=eh1",
		"spring.cloud.azure.eventhubs.consumer.consumer-group=$default" })
@Testcontainers
class SpringCloudAzureEventHubsApplicationTests {

	private static final Network network = Network.newNetwork();

	private static final int AZURE_STORAGE_BLOB_PORT = 10000;

	private static final int AZURE_STORAGE_QUEUE_PORT = 10001;

	private static final int AZURE_STORAGE_TABLE_PORT = 10002;

	private static final int AZURE_EVENTHUBS_BLOB_PORT = 5672;

	@Container
	private static final GenericContainer<?> azurite = new GenericContainer<>(
			"mcr.microsoft.com/azure-storage/azurite:latest")
		.withExposedPorts(AZURE_STORAGE_BLOB_PORT, AZURE_STORAGE_QUEUE_PORT, AZURE_STORAGE_TABLE_PORT)
		.withNetwork(network)
		.withNetworkAliases("azurite");

	@Container
	private static final GenericContainer<?> eventHubs = new GenericContainer<>(
			"mcr.microsoft.com/azure-messaging/eventhubs-emulator:latest")
		.withExposedPorts(AZURE_EVENTHUBS_BLOB_PORT)
		.withCopyFileToContainer(MountableFile.forClasspathResource("Config.json"),
				"/Eventhubs_Emulator/ConfigFiles/Config.json")
		.waitingFor(Wait.forLogMessage(".*Emulator Service is Successfully Up!.*", 1))
		.withNetwork(network)
		.withEnv("BLOB_SERVER", "azurite")
		.withEnv("METADATA_SERVER", "azurite")
		.withEnv("ACCEPT_EULA", "Y");

	@Autowired
	private EventHubProducerClient producerClient;

	@Autowired
	private EventHubConsumerClient consumerClient;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		var eventHubsHost = eventHubs.getHost();
		var eventHubsMappedPort = eventHubs.getMappedPort(AZURE_EVENTHUBS_BLOB_PORT);
		var connectionString = "Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;"
			.formatted(eventHubsHost, eventHubsMappedPort);
		registry.add("spring.cloud.azure.eventhubs.connection-string", () -> connectionString);
	}

	@Test
	void contextLoads() {
		this.producerClient.send(List.of(new EventData("test message")));

		waitAtMost(Duration.ofSeconds(30)).pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
			IterableStream<PartitionEvent> events = this.consumerClient.receiveFromPartition("0", 1,
					EventPosition.earliest(), Duration.ofSeconds(2));
			Iterator<PartitionEvent> iterator = events.stream().iterator();
			assertThat(iterator.hasNext()).isTrue();
			assertThat(iterator.next().getData().getBodyAsString()).isEqualTo("test message");
		});
	}

}