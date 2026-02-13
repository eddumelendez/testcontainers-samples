package com.example.springbootelasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.restassured.response.Response;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.boot.elasticsearch.autoconfigure.Rest5ClientBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@DataElasticsearchTest
@Testcontainers
class BookRepositoryElasticsearch8ApiKeyTests {

	static String apiKey;

	@Container
	@ServiceConnection
	private static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:9.2.3") {
		@Override
		protected void containerIsStarted(InspectContainerResponse containerInfo) {
			Response response = given().baseUri("http://" + elasticsearch.getHttpHostAddress())
				.auth()
				.preemptive()
				.basic("elastic", "changeme")
				.header("Content-Type", "application/json")
				.body("{\"name\": \"tc\"}")
				.post("/_security/api_key");
			apiKey = response.getBody().jsonPath().getString("encoded");

			// Response response1 = given().baseUri("http://" +
			// elasticsearch.getHttpHostAddress())
			// .header("Authorization", "ApiKey " + apiKey)
			// .queryParam("acknowledge", "true")
			// .post("/_license/start_basic");
			// System.out.println(response1.getBody().prettyPrint());
		}
	}.withEnv("xpack.security.http.ssl.enabled", "false").withEnv("xpack.license.self_generated.type", "trial");

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Test
	void contextLoads() {
		String id = UUID.randomUUID().toString();
		this.bookRepository.save(new Book(id, "Spring Boot Testing"));

		assertThat(this.elasticsearchTemplate.get(id, Book.class)).extracting("title").isEqualTo("Spring Boot Testing");
	}

	@TestConfiguration
	static class Config {

		@Bean
		Rest5ClientBuilderCustomizer elasticsearchRestClientBuilderCustomizer() {
			return new Rest5ClientBuilderCustomizer() {

				@Override
				public void customize(Rest5ClientBuilder builder) {

				}

				@Override
				public void customize(HttpAsyncClientBuilder builder) {
					builder.setDefaultHeaders(List.of(new BasicHeader("Authorization", "ApiKey " + apiKey)));
				}

				@Override
				public void customize(RequestConfig.Builder builder) {
					Rest5ClientBuilderCustomizer.super.customize(builder);
				}
			};
		}

	}

}
