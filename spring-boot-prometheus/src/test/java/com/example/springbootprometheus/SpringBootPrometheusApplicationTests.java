package com.example.springbootprometheus;

import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.awaitility.Awaitility;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "management.endpoints.web.exposure.include=*", "management.prometheus.metrics.export.step=2s" })
@AutoConfigureMetrics
@AutoConfigureRestTestClient
@Testcontainers
class SpringBootPrometheusApplicationTests {

	@LocalServerPort
	private int localPort;

	@Autowired
	private RestTestClient restTestClient;

	@Container
	static final GenericContainer<?> prometheus = new GenericContainer<>("prom/prometheus:v2.37.0")
		.withExposedPorts(9090)
		.waitingFor(Wait.forLogMessage("(?s).*Server is ready to receive web requests.*$", 1))
		.withAccessToHost(true);

	@BeforeEach
	void setUp() {
		org.testcontainers.Testcontainers.exposeHostPorts(this.localPort);

		var config = """
				scrape_configs:
				  - job_name: "prometheus"
				    scrape_interval: 2s
				    metrics_path: "/actuator/prometheus"
				    static_configs:
				      - targets: ['host.testcontainers.internal:%s']
				""".formatted(this.localPort);
		prometheus.copyFileToContainer(Transferable.of(config), "/etc/prometheus/prometheus.yml");

		// Reload config
		prometheus.getDockerClient().killContainerCmd(prometheus.getContainerId()).withSignal("SIGHUP").exec();
	}

	@Test
	void contextLoads() {
		var restTestClient = RestTestClient.bindToServer()
			.baseUrl("http://%s:%d".formatted(prometheus.getHost(), prometheus.getMappedPort(9090)))
			.build();

		this.restTestClient.get().uri("/greetings").exchange().expectBody(String.class).isEqualTo("Hello World");

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> restTestClient.get()
				.uri(UriComponentsBuilder.fromPath("/api/v1/query")
					.queryParam("query", "http_server_requests_seconds_count{uri=\"/greetings\"}")
					.build()
					.toUri())
				.exchange()
				.expectBody()
				.jsonPath("data.result[0].value")
				.value(JSONArray.class, value -> assertThat(value).contains("1")));
	}

}
