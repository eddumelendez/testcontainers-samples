package com.example.springcloudazureblob;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.util.StreamUtils;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringCloudAzureStorageBlobApplicationTests {

	@Container
	@ServiceConnection
	private static final AzuriteContainer azurite = new AzuriteContainer(
			"mcr.microsoft.com/azure-storage/azurite:latest");

	@Value("azure-blob://testcontainers/message.txt")
	private Resource blobFile;

	@Test
	void contextLoads() throws IOException {
		try (OutputStream os = ((WritableResource) this.blobFile).getOutputStream()) {
			os.write("Local Cloud Development with Testcontainers".getBytes());
		}
		var content = StreamUtils.copyToString(this.blobFile.getInputStream(), Charset.defaultCharset());
		assertThat(content).isEqualTo("Local Cloud Development with Testcontainers");
	}

}
