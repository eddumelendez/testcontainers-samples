package com.example.consumer;

import com.example.container.KafkaLocalContainer;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class SpringBootKafkaLocalRaftApplicationTests {

	private static final String APPLICATION_VND_KAFKA_JSON_V_2_JSON = "application/vnd.kafka.json.v2+json";

	@Container
	static KafkaLocalContainer kafka = new KafkaLocalContainer("confluentinc/confluent-local:7.4.1");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServer);
	}

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private TestListener testListener;

	@Test
	void consumeMessage() {
		this.kafkaTemplate.send("test", "test-data");

		waitAtMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(this.testListener.messages).hasSize(1);
		});

		var restProxy = kafka.getRestProxyUrl();
		RestAssured.given()
			.contentType(APPLICATION_VND_KAFKA_JSON_V_2_JSON)
			.accept("application/vnd.kafka.v2+json")
			.body("""
					{"records":[{"key":"jsmith","value":"alarm clock"},{"key":"htanaka","value":"batteries"},{"key":"awalther","value":"bookshelves"}]}
					""")
			.post("%s/topics/purchases".formatted(restProxy))
			.then()
			.statusCode(200);

		RestAssured.given().contentType(APPLICATION_VND_KAFKA_JSON_V_2_JSON).body("""
				{"name": "ci1", "format": "json", "auto.offset.reset": "earliest"}
				""").post("%s/consumers/cg1".formatted(restProxy)).then().statusCode(200);
		RestAssured.given().contentType(APPLICATION_VND_KAFKA_JSON_V_2_JSON).body("""
				{"topics":["purchases"]}
				""").post("%s/consumers/cg1/instances/ci1/subscription".formatted(restProxy)).then().statusCode(204);

		await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(10)).untilAsserted(() -> {
			var response = RestAssured.given()
				.accept(APPLICATION_VND_KAFKA_JSON_V_2_JSON)
				.get("%s/consumers/cg1/instances/ci1/records".formatted(restProxy))
				.getBody()
				.as(new TypeRef<List<Map<String, String>>>() {
				});
			assertThat(response).hasSize(3).extracting("key").containsExactly("jsmith", "htanaka", "awalther");
		});
	}

	@TestConfiguration
	static class Config {

		@Bean
		TestListener testListener() {
			return new TestListener();
		}

	}

	static class TestListener {

		private final List<String> messages = new ArrayList<>();

		@KafkaListener(topics = "test", groupId = "test")
		void listen(String data) {
			this.messages.add(data);
		}

	}

}
