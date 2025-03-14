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
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.azure.EventHubsEmulatorContainer;
import org.testcontainers.containers.Network;
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

	@Container
	private static final AzuriteContainer azurite = new AzuriteContainer(
			"mcr.microsoft.com/azure-storage/azurite:latest")
		.withNetwork(network);

	@Container
	private static final EventHubsEmulatorContainer eventHubs = new EventHubsEmulatorContainer(
			"mcr.microsoft.com/azure-messaging/eventhubs-emulator:latest")
		.withConfig(MountableFile.forClasspathResource("Config.json"))
		.withNetwork(network)
		.acceptLicense()
		.withAzuriteContainer(azurite);

	@Autowired
	private EventHubProducerClient producerClient;

	@Autowired
	private EventHubConsumerClient consumerClient;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.azure.eventhubs.connection-string", eventHubs::getConnectionString);
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