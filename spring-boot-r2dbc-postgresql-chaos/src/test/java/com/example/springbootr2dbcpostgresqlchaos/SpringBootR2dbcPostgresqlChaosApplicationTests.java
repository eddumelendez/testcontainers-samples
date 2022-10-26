package com.example.springbootr2dbcpostgresqlchaos;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.io.IOException;

@Testcontainers
@DataR2dbcTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class SpringBootR2dbcPostgresqlChaosApplicationTests {

	private static final Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withNetwork(network);

	@Container
	private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
			.withNetwork(network);

	private static Proxy postgresqlProxy;

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) throws IOException {
		var toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
		var postgresAlias = postgres.getNetworkAliases().get(0);
		postgresqlProxy = toxiproxyClient.createProxy("postgresql", "0.0.0.0:8666", "%s:5432".formatted(postgresAlias));

		registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(toxiproxy.getHost(),
				toxiproxy.getMappedPort(8666), postgres.getDatabaseName()));
		registry.add("spring.r2dbc.username", postgres::getUsername);
		registry.add("spring.r2dbc.password", postgres::getPassword);
		registry.add("spring.flyway.url", postgres::getJdbcUrl);
		registry.add("spring.flyway.user", postgres::getUsername);
		registry.add("spring.flyway.password", postgres::getPassword);
	}

	@Autowired
	private ProfileRepository profileRepository;

	@Test
	void normal() {
		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

	@Test
	void withLatency() throws IOException {
		postgresqlProxy.toxics().latency("postgresql-latency", ToxicDirection.DOWNSTREAM, 2100).setJitter(100);

		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

	@Test
	void withTimeout() throws IOException {
		postgresqlProxy.toxics().timeout("postgresql-timeout", ToxicDirection.UPSTREAM, 1000);

		StepVerifier.create(this.profileRepository.findAll())
				.expectErrorMatches(throwable -> throwable.getMessage().contains("Connection unexpectedly closed"))
				.verify();
	}

	@Test
	void withToxiProxyConnectionDown() throws IOException {
		postgresqlProxy.disable();

		StepVerifier.create(this.profileRepository.findAll())
				.expectErrorMatches(throwable -> throwable.getMessage().contains("Connection unexpectedly closed"))
				.verify();
	}

}
