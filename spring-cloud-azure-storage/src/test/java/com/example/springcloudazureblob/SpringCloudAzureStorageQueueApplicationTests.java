package com.example.springcloudazureblob;

import com.azure.storage.queue.QueueClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.cloud.azure.storage.queue.queue-name=devstoreaccount1/tc-queue" })
@Testcontainers
class SpringCloudAzureStorageQueueApplicationTests {

	@Container
	@ServiceConnection
	private static final AzuriteContainer azurite = new AzuriteContainer(
			"mcr.microsoft.com/azure-storage/azurite:latest");

	@Autowired
	private QueueClient queueClient;

	@Test
	void contextLoads() {
		this.queueClient.create();
		this.queueClient.sendMessage("Local Cloud Development with Testcontainers");

		var messageItem = this.queueClient.receiveMessage();
		assertThat(messageItem.getBody().toString()).isEqualTo("Local Cloud Development with Testcontainers");
	}

}
