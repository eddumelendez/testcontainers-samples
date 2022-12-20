package com.example.springbootyugabytedbliquibase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class YugabyteDbTest {

	@Container
	private static final YugabyteDBYSQLContainer yugabytedb = new YugabyteDBYSQLContainer(
			"yugabytedb/yugabyte:2.14.4.0-b26");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", yugabytedb::getJdbcUrl);
		registry.add("spring.datasource.username", yugabytedb::getUsername);
		registry.add("spring.datasource.password", yugabytedb::getPassword);
		registry.add("spring.datasource.driver-class-name", yugabytedb::getDriverClassName);
	}

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
