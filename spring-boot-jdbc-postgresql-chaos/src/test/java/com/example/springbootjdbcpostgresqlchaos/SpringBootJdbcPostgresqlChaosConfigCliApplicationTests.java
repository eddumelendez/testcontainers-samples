package com.example.springbootjdbcpostgresqlchaos;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.vavr.control.Try;
import org.assertj.vavr.api.VavrAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJdbcTest
class SpringBootJdbcPostgresqlChaosConfigCliApplicationTests {

	private static final Logger logger = LoggerFactory
		.getLogger(SpringBootJdbcPostgresqlChaosConfigCliApplicationTests.class);

	private static final Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
		.withNetwork(network)
		.withNetworkAliases("postgres");

	@Container
	private static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.11.0")
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

		TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofMillis(50));
		Supplier<CompletableFuture<Iterable<Profile>>> completableFutureSupplier = () -> CompletableFuture
			.supplyAsync(() -> this.profileRepository.findAll());
		Try<Iterable<Profile>> actual = Try.ofCallable(timeLimiter.decorateFutureSupplier(completableFutureSupplier));
		VavrAssertions.assertThat(actual).isFailure().failBecauseOf(TimeoutException.class);

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");
	}

	@Test
	void withLatencyWithRetries() throws Exception {
		var intervalFunction = IntervalFunction.of(Duration.ofMillis(500));
		var retryConfig = RetryConfig.custom()
			.retryExceptions(TimeoutException.class)
			.maxAttempts(2)
			.failAfterMaxAttempts(true)
			.intervalFunction(intervalFunction)
			.build();
		var jdbcRetry = Retry.of("jdbc", retryConfig);

		var timeLimiter = TimeLimiter.of(Duration.ofMillis(500));

		execute("./toxiproxy-cli toxic add -t latency --downstream -a latency=1600 -a jitter=100 -n latency_downstream postgresql");

		Supplier<CompletableFuture<Iterable<Profile>>> completableFutureSupplier = () -> CompletableFuture
			.supplyAsync(() -> {
				logger.info("Executing query");
				return this.profileRepository.findAll();
			});
		Callable<Iterable<Profile>> iterableCallable = timeLimiter.decorateFutureSupplier(completableFutureSupplier);
		Callable<Iterable<Profile>> iterableCallable1 = Retry.decorateCallable(jdbcRetry, iterableCallable);
		VavrAssertions.assertThat(Try.ofCallable(iterableCallable1)).isFailure().failBecauseOf(TimeoutException.class);

		execute("./toxiproxy-cli toxic remove -n latency_downstream postgresql");

		assertThat(this.profileRepository.findAll()).hasSize(4);
	}

	@Test
	void withToxiProxyConnectionDown() throws Exception {
		execute("./toxiproxy-cli toxic add -t bandwidth --downstream -a rate=0 -n bandwidth_downstream postgresql");
		execute("./toxiproxy-cli toxic add -t bandwidth --upstream -a rate=0 -n bandwidth_upstream postgresql");

		TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofMillis(50));
		Supplier<CompletableFuture<Iterable<Profile>>> completableFutureSupplier = () -> CompletableFuture
			.supplyAsync(() -> this.profileRepository.findAll());
		Try<Iterable<Profile>> actual = Try.ofCallable(timeLimiter.decorateFutureSupplier(completableFutureSupplier));
		VavrAssertions.assertThat(actual).isFailure().failBecauseOf(TimeoutException.class);

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
