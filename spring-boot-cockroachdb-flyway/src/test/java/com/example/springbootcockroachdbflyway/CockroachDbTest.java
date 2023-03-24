package com.example.springbootcockroachdbflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcServiceConnection;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CockroachDbTest {

	@Container
	@JdbcServiceConnection
	private static final CockroachContainer cockroachdb = new CockroachContainer("cockroachdb/cockroach:v22.2.0");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
