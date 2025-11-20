package com.example.springbootjdbcpostgresqlchaos;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJdbcTest(properties = { "spring.datasource.hikari.connection-timeout=250",
		"spring.datasource.hikari.validation-timeout=250", "spring.datasource.hikari.maximum-pool-size=1",
		"spring.datasource.hikari.login-timeout=1" })
class SpringBootJdbcPostgresqlFailsafeChaosConfigCliApplicationTests {

	private static final Logger logger = LoggerFactory
		.getLogger(SpringBootJdbcPostgresqlFailsafeChaosConfigCliApplicationTests.class);

	private static final Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
		.withNetwork(network)
		.withNetworkAliases("postgres");

	@Container
	private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0")
		.withCopyFileToContainer(MountableFile.forClasspathResource("toxiproxy.json"), "/tmp/toxiproxy.json")
		.withCommand("-host=0.0.0.0", "-config=/tmp/toxiproxy.json")
		.withNetwork(network);

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) throws Exception {
		registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/%s".formatted(toxiproxy.getHost(),
				toxiproxy.getMappedPort(8666), postgres.getDatabaseName()));
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.flyway.url", postgres::getJdbcUrl);
		registry.add("spring.flyway.user", postgres::getUsername);
		registry.add("spring.flyway.password", postgres::getPassword);
	}

	@Autowired
	private ProfileRepository profileRepository;

	@Test
	void normal() {
		assertThat(this.profileRepository.findAll()).hasSize(4);
	}

	@Test
	void withLatency() throws Exception {
		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

		assertThat(this.profileRepository.findAll()).hasSize(4);

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
	}

	@Test
	void withLatencyWithTimeout() throws Exception {
		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

		Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(50))
			.onFailure(e -> logger.info("Attempt #{}", e.getAttemptCount()))
			.withInterrupt()
			.build();

		assertThatThrownBy(() -> Failsafe.with(timeout).get(this.profileRepository::findAll))
			.isInstanceOf(TimeoutExceededException.class);

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
	}

	@Test
	void withLatencyWithRetries() throws Exception {
		Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(500)).withInterrupt().build();

		RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
			.handle(TimeoutExceededException.class)
			.withMaxRetries(2)
			.withDelay(Duration.ofMillis(500))
			.onRetry(e -> logger.info("Retry #{}", e.getAttemptCount()))
			.build();

		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

		assertThatThrownBy(() -> Failsafe.with(retryPolicy).compose(timeout).get(this.profileRepository::findAll))
			.isInstanceOf(TimeoutExceededException.class);

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");

		assertThat(this.profileRepository.findAll()).hasSize(4);
	}

	@Test
	void withToxiProxyConnectionDown() throws Exception {
		Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(500))
			.withInterrupt()
			.onFailure(e -> logger.info("Timeout produced"))
			.build();

		execute("./toxiproxy-cli toxic add -t bandwidth --downstream -a rate=0 -n bandwidth_downstream postgresql");
		execute("./toxiproxy-cli toxic add -t bandwidth --upstream -a rate=0 -n bandwidth_upstream postgresql");

		assertThatThrownBy(() -> Failsafe.with(timeout).get(this.profileRepository::findAll))
			.isInstanceOf(TimeoutExceededException.class);

		execute("./toxiproxy-cli toxic remove -n bandwidth_downstream postgresql");
		execute("./toxiproxy-cli toxic remove -n bandwidth_upstream postgresql");

		assertThat(this.profileRepository.findAll()).hasSize(4);
	}

	private static void execute(String command) throws Exception {
		ExecResult result = toxiproxy.execInContainer(command.split(" "));
		if (result.getExitCode() != 0) {
			throw new RuntimeException("Error executing command '%s' \nstderr: %s\nstdout: %s".formatted(command,
					result.getStderr(), result.getStdout()));
		}
	}

}
