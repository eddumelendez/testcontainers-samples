package com.example.springbootelasticsearch;

import com.github.dockerjava.api.command.InspectContainerResponse;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.Ssl;
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
class BookRepositoryElasticsearch8SslApiKeyTests {

	static String apiKey;

	@Container
	@ServiceConnection
	@Ssl
	private static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:8.7.1") {
		@Override
		protected void containerIsStarted(InspectContainerResponse containerInfo) {
			Response response = given().baseUri("https://" + elasticsearch.getHttpHostAddress())
				.config(RestAssuredConfig.config()
					.sslConfig(SSLConfig.sslConfig()
						.sslSocketFactory(new SSLSocketFactory(elasticsearch.createSslContextFromCa()))))
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
	}.withEnv("xpack.license.self_generated.type", "trial");

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
		RestClientBuilderCustomizer elasticsearchRestClientBuilderCustomizer() {
			return new RestClientBuilderCustomizer() {

				@Override
				public void customize(RestClientBuilder builder) {

				}

				@Override
				public void customize(HttpAsyncClientBuilder builder) {
					builder.setDefaultHeaders(List.of(new BasicHeader("Authorization", "ApiKey " + apiKey)));
				}

				@Override
				public void customize(RequestConfig.Builder builder) {
					RestClientBuilderCustomizer.super.customize(builder);
				}
			};
		}

	}

}
