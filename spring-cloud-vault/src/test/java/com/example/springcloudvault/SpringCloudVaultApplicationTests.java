package com.example.springcloudvault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.vault.application-name=tc", "spring.cloud.vault.token=tc-token",
				"spring.cloud.vault.scheme=http" })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
class SpringCloudVaultApplicationTests {

	@LocalServerPort
	private int port;

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
		given().port(this.port).get("/secrets/message").then().assertThat().body(containsString("spring loves tc"));
	}

}
