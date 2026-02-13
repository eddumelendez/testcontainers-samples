package com.example.springbootelasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.Ssl;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataElasticsearchTest
@Testcontainers
class BookRepositoryElasticsearch8Tests {

	@Container
	@ServiceConnection
	@Ssl
	private static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:9.2.3");

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

}
