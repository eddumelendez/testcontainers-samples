package com.example.springbootprometheus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=*",
                "management.metrics.export.prometheus.step=2s"})
@AutoConfigureMetrics
@Testcontainers
class SpringBootPrometheusApplicationTests {

    @LocalServerPort
    private int localPort;

    private static GenericContainer prometheus;

    @BeforeEach
    void setUp() {
        org.testcontainers.Testcontainers.exposeHostPorts(this.localPort);
        if (prometheus == null) {
            prometheus = createPrometheus();
            prometheus.start();
        }
    }

    @AfterAll
    static void tearDown() {
        if (prometheus != null) {
            prometheus.stop();
        }
    }

    @Test
    void contextLoads() {
        given().port(this.localPort)
                .get("/greetings")
                .then()
                .assertThat()
                .body(equalTo("Hello World"));
        Awaitility.given().pollInterval(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(15))
                .ignoreExceptions()
                .untilAsserted(() ->
                        given().baseUri("http://" + prometheus.getHost())
                                .port(prometheus.getMappedPort(9090))
                                .queryParams(Map.of("query", "http_server_requests_seconds_count{uri=\"/greetings\"}"))
                                .get("/api/v1/query")
                                .prettyPeek()
                                .then()
                                .assertThat()
                                .statusCode(200)
                                .body("data.result[0].value", hasItem("1")));
    }

    private GenericContainer createPrometheus() {
        var config = """
                scrape_configs:
                  - job_name: "prometheus"
                    scrape_interval: 2s
                    metrics_path: "/actuator/prometheus"
                    static_configs:
                      - targets: ['host.testcontainers.internal:%s']
                """.formatted(this.localPort);
        return new GenericContainer<>("prom/prometheus:v2.37.0")
                .withExposedPorts(9090)
                .withCopyToContainer(Transferable.of(config), "/etc/prometheus/prometheus.yml")
                .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*Server is ready to receive web requests.*$"))
                .withAccessToHost(true);
    }

}
