package com.example.springcloudvault;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.vault.application-name=tc", "spring.cloud.vault.token=tc-token",
				"spring.cloud.vault.scheme=http" })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
@AutoConfigureRestTestClient
class SpringCloudVaultApplicationTests {

	@Autowired
	private RestTestClient restTestClient;

	@Container
	private static VaultContainer vault = new VaultContainer("hashicorp/vault:1.12.0").withVaultToken("tc-token")
		.withInitCommand("kv put secret/tc message=\"spring loves tc\"");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.vault.host", vault::getHost);
		registry.add("spring.cloud.vault.port", vault::getFirstMappedPort);
		registry.add("spring.config.import", () -> "vault://secret/tc");
	}

	@Test
	void contextLoads() {
		this.restTestClient.get()
			.uri("/secrets/message")
			.exchange()
			.expectBody(String.class)
			.isEqualTo("spring loves tc");
	}

}
