package com.example.springcloudvault;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.config.import=vault:", "spring.cloud.vault.application-name=tc",
				"spring.cloud.vault.token=tc-token", "spring.cloud.vault.scheme=http" })
@Testcontainers
class SpringCloudVaultApplicationTests {

	@LocalServerPort
	private int port;

	@Container
	private static VaultContainer vault = new VaultContainer("hashicorp/vault:1.12.0").withVaultToken("tc-token")
		.withInitCommand("kv put secret/tc message=\"spring loves tc\"");

	@BeforeAll
	static void beforeAll() {
		System.setProperty("spring.cloud.vault.host", vault.getHost());
		System.setProperty("spring.cloud.vault.port", String.valueOf(vault.getFirstMappedPort()));
	}

	@Test
	void contextLoads() {
		given().port(this.port).get("/secrets/message").then().assertThat().body(containsString("spring loves tc"));
	}

}
