package com.example.springbootr2dbcpostgresqlchaos;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.toxic.Latency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataR2dbcTest
class SpringBootR2dbcPostgresqlChaosApplicationTests {

	private static final Logger logger = LoggerFactory.getLogger(SpringBootR2dbcPostgresqlChaosApplicationTests.class);

	private static final Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
		.withNetwork(network)
		.withNetworkAliases("postgres");

	@Container
	private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
		.withNetwork(network);

	private static Proxy postgresqlProxy;

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) throws IOException {
		var toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
		postgresqlProxy = toxiproxyClient.createProxy("postgresql", "0.0.0.0:8666", "postgres:5432");

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

	@BeforeEach
	void setUp() throws IOException {
		postgresqlProxy.toxics().getAll().forEach(toxic -> {
			try {
				toxic.remove();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void normal() {
		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

	@Test
	void withLatency() throws IOException {
		postgresqlProxy.toxics().latency("postgresql-latency", ToxicDirection.DOWNSTREAM, 1600).setJitter(100);

		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

	@Test
	void withLatencyWithTimeout() throws IOException {
		postgresqlProxy.toxics().latency("postgresql-latency", ToxicDirection.DOWNSTREAM, 1600).setJitter(100);
		StepVerifier.create(this.profileRepository.findAll().timeout(Duration.ofMillis(50)))
			.expectError(TimeoutException.class)
			.verify();
	}

	@Test
	void withLatencyWithRetries() throws IOException {
		Latency latency = postgresqlProxy.toxics()
			.latency("postgresql-latency", ToxicDirection.DOWNSTREAM, 1600)
			.setJitter(100);

		StepVerifier
			.create(this.profileRepository.findAll()
				.timeout(Duration.ofSeconds(1))
				.retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1))
					.filter(throwable -> throwable instanceof TimeoutException)
					.doBeforeRetry(retrySignal -> logger.info(retrySignal.copy().toString()))))
			.expectSubscription()
			.expectNoEvent(Duration.ofSeconds(4))
			.then(() -> {
				try {
					latency.remove();
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			})
			.expectNextCount(4)
			.expectComplete()
			.verify();
	}

	@Test
	void withToxiProxyConnectionDown() throws IOException {
		postgresqlProxy.toxics().bandwidth("postgres-cut-connection-downstream", ToxicDirection.DOWNSTREAM, 0);
		postgresqlProxy.toxics().bandwidth("postgres-cut-connection-upstream", ToxicDirection.UPSTREAM, 0);

		StepVerifier.create(this.profileRepository.findAll().timeout(Duration.ofSeconds(5)))
			.verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(TimeoutException.class));

		postgresqlProxy.toxics().get("postgres-cut-connection-downstream").remove();
		postgresqlProxy.toxics().get("postgres-cut-connection-upstream").remove();

		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

}
