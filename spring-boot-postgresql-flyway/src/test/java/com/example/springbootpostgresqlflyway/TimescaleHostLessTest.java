package com.example.springbootpostgresqlflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJdbcTest(properties = { "spring.datasource.url=jdbc:tc:timescaledb:2.7.2-pg14:///" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TimescaleHostLessTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select count(*) from profile");
		assertThat(records).hasSize(1);
	}

}
