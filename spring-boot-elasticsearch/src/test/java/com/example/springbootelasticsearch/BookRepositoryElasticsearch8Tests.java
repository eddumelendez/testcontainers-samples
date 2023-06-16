package com.example.springbootelasticsearch;

import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataElasticsearchTest(
		properties = { "spring.elasticsearch.username=elastic", "spring.elasticsearch.password=changeme" })
@Testcontainers
class BookRepositoryElasticsearch8Tests {

	@Container
	private static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:8.7.1");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.elasticsearch.uris", () -> "https://" + elasticsearch.getHttpHostAddress());
	}

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
	static class SSL {

		@Bean
		public RestClientBuilderCustomizer customizer() {
			return new RestClientBuilderCustomizer() {
				@Override
				public void customize(RestClientBuilder builder) {

				}

				@Override
				public void customize(HttpAsyncClientBuilder builder) {
					builder.setSSLContext(elasticsearch.createSslContextFromCa());
				}
			};
		}

	}

}
