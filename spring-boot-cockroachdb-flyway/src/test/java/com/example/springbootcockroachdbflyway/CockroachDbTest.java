package com.example.springbootcockroachdbflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CockroachDbTest {

	@Container
	private static final CockroachContainer cockroachdb = new CockroachContainer("cockroachdb/cockroach:v22.2.0");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", cockroachdb::getJdbcUrl);
		registry.add("spring.datasource.username", cockroachdb::getUsername);
		registry.add("spring.datasource.password", cockroachdb::getPassword);
	}

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
