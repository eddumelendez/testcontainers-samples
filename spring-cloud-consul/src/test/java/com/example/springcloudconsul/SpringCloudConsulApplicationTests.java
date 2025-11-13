package com.example.springcloudconsul;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.config.import=consul:", "spring.cloud.consul.config.name=tc" })
@Testcontainers
@AutoConfigureRestTestClient
class SpringCloudConsulApplicationTests {

	@Autowired
	private RestTestClient restTestClient;

	@Container
	private static ConsulContainer consul = new ConsulContainer("hashicorp/consul:1.13.2")
		.withConsulCommand("kv put config/tc/message Hello");

	@BeforeAll
	static void beforeAll() {
		System.setProperty("spring.cloud.consul.host", consul.getHost());
		System.setProperty("spring.cloud.consul.port", String.valueOf(consul.getFirstMappedPort()));
		// or
		// System.setProperty("spring.config.import=", String.format("consul:%s:%d",
		// consul.getHost(), consul.getFirstMappedPort()));
	}

	@Test
	void contextLoads() {
		this.restTestClient.get().uri("/hello").exchange().expectBody(String.class).isEqualTo("Hello");
	}

}
