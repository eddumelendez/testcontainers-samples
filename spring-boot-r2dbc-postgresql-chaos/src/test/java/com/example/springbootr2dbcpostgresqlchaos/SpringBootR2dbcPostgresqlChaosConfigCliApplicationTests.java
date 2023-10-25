package com.example.springbootr2dbcpostgresqlchaos;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataR2dbcTest
class SpringBootR2dbcPostgresqlChaosConfigCliApplicationTests {

	private static final Logger logger = LoggerFactory
		.getLogger(SpringBootR2dbcPostgresqlChaosConfigCliApplicationTests.class);

	private static final Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
		.withNetwork(network)
		.withNetworkAliases("postgres");

	@Container
	private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.7.0")
		.withCopyFileToContainer(MountableFile.forClasspathResource("toxiproxy.json"), "/tmp/toxiproxy.json")
		.withCommand("-host=0.0.0.0", "-config=/tmp/toxiproxy.json")
		.withNetwork(network);

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) throws Exception {
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
	void withLatency() throws Exception {
		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
	}

	@Test
	void withLatencyWithTimeout() throws Exception {
		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");
		StepVerifier.create(this.profileRepository.findAll().timeout(Duration.ofMillis(50)))
			.expectError(TimeoutException.class)
			.verify();

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
	}

	@Test
	void withLatencyWithRetries() throws Exception {
		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

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
					execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			})
			.expectNextCount(4)
			.expectComplete()
			.verify();
	}

	@Test
	void withToxiProxyConnectionDown() throws Exception {
		execute("./toxiproxy-cli toxic add -t bandwidth --downstream -a rate=0 -n bandwidth_downstream postgresql");
		execute("./toxiproxy-cli toxic add -t bandwidth --upstream -a rate=0 -n bandwidth_upstream postgresql");

		StepVerifier.create(this.profileRepository.findAll().timeout(Duration.ofSeconds(5)))
			.verifyErrorSatisfies(throwable -> assertThat(throwable).isInstanceOf(TimeoutException.class));

		execute("./toxiproxy-cli toxic remove -n bandwidth_downstream postgresql");
		execute("./toxiproxy-cli toxic remove -n bandwidth_upstream postgresql");

		StepVerifier.create(this.profileRepository.findAll()).expectNextCount(4).verifyComplete();
	}

	private static void execute(String command) throws Exception {
		ExecResult result = toxiproxy.execInContainer(command.split(" "));
		if (result.getExitCode() != 0) {
			throw new RuntimeException("Error executing command '%s' \nstderr: %s\nstdout: %s".formatted(command,
					result.getStderr(), result.getStdout()));
		}
	}

}
