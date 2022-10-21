package com.example.springcloudazureblob;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringCloudAzureStorageBlobApplicationTests {

	private static final int AZURE_STORAGE_BLOB_PORT = 10000;

	@Container
	private static final GenericContainer<?> azurite = new GenericContainer<>(
			"mcr.microsoft.com/azure-storage/azurite:latest").withExposedPorts(AZURE_STORAGE_BLOB_PORT);

	@Value("azure-blob://testcontainers/message.txt")
	private Resource blobFile;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		var azuriteHost = azurite.getHost();
		var azuriteBlobMappedPort = azurite.getMappedPort(AZURE_STORAGE_BLOB_PORT);
		var connectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://%s:%d/devstoreaccount1;"
				.formatted(azuriteHost, azuriteBlobMappedPort);
		registry.add("spring.cloud.azure.storage.blob.connection-string", () -> connectionString);
	}

	@Test
	void contextLoads() throws IOException {
		try (OutputStream os = ((WritableResource) this.blobFile).getOutputStream()) {
			os.write("Local Cloud Development with Testcontainers".getBytes());
		}
		var content = StreamUtils.copyToString(this.blobFile.getInputStream(), Charset.defaultCharset());
		assertThat(content).isEqualTo("Local Cloud Development with Testcontainers");
	}

}
