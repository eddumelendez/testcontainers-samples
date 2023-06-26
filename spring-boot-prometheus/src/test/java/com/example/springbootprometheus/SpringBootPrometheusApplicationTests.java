package com.example.springbootprometheus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "management.endpoints.web.exposure.include=*", "management.prometheus.metrics.export.step=2s" })
@AutoConfigureObservability(tracing = false)
@Testcontainers
class SpringBootPrometheusApplicationTests {

	@LocalServerPort
	private int localPort;

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
		given().port(this.localPort).get("/greetings").then().assertThat().body(equalTo("Hello World"));
		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> given().baseUri("http://" + prometheus.getHost())
				.port(prometheus.getMappedPort(9090))
				.queryParams(Map.of("query", "http_server_requests_seconds_count{uri=\"/greetings\"}"))
				.get("/api/v1/query")
				.prettyPeek()
				.then()
				.assertThat()
				.statusCode(200)
				.body("data.result[0].value", hasItem("1")));
	}

}
