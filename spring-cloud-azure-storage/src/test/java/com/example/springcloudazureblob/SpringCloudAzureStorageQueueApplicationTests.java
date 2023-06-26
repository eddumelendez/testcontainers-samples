package com.example.springcloudazureblob;

import com.azure.storage.queue.QueueClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.cloud.azure.storage.queue.queue-name=devstoreaccount1/tc-queue" })
@Testcontainers
class SpringCloudAzureStorageQueueApplicationTests {

	private static final int AZURE_STORAGE_QUEUE_PORT = 10001;

	@Container
	private static final GenericContainer<?> azurite = new GenericContainer<>(
			"mcr.microsoft.com/azure-storage/azurite:latest")
		.withExposedPorts(AZURE_STORAGE_QUEUE_PORT);

	@Autowired
	private QueueClient queueClient;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		var azuriteHost = azurite.getHost();
		var azuriteQueueMappedPort = azurite.getMappedPort(AZURE_STORAGE_QUEUE_PORT);
		var queueEndpoint = "http://%s:%d".formatted(azuriteHost, azuriteQueueMappedPort);
		var connectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;QueueEndpoint=%s/devstoreaccount1;"
			.formatted(queueEndpoint);
		registry.add("spring.cloud.azure.storage.queue.connection-string", () -> connectionString);
		registry.add("spring.cloud.azure.storage.queue.endpoint", () -> queueEndpoint);
	}

	@Test
	void contextLoads() {
		this.queueClient.create();
		this.queueClient.sendMessage("Local Cloud Development with Testcontainers");

		var messageItem = this.queueClient.receiveMessage();
		assertThat(messageItem.getBody().toString()).isEqualTo("Local Cloud Development with Testcontainers");
	}

}
