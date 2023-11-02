package com.example.springcloudconsul;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "spring.cloud.consul.config.name=tc" })
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@Testcontainers
class SpringCloudConsulApplicationTests {

	@LocalServerPort
	private int port;

	@Container
	private static ConsulContainer consul = new ConsulContainer("hashicorp/consul:1.13.2")
		.withConsulCommand("kv put config/tc/message Hello");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.consul.host", consul::getHost);
		registry.add("spring.cloud.consul.port", consul::getFirstMappedPort);
		registry.add("spring.config.import",
				() -> "consul:%s:%d".formatted(consul.getHost(), consul.getFirstMappedPort()));
	}

	@Test
	void contextLoads() {
		given().port(this.port).get("/hello").then().assertThat().body(containsString("Hello"));
	}

}
