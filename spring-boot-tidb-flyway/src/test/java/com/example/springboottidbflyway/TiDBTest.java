package com.example.springboottidbflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.tidb.TiDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TiDBTest {

	@Container
	private static final TiDBContainer tidb = new TiDBContainer("pingcap/tidb:v6.2.0");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", tidb::getJdbcUrl);
		registry.add("spring.datasource.username", tidb::getUsername);
		registry.add("spring.datasource.password", tidb::getPassword);
	}

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select count(*) from profile");
		assertThat(records).hasSize(1);
	}

}
