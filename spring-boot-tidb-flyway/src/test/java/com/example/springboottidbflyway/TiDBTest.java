package com.example.springboottidbflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.tidb.TiDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJdbcTest
class TiDBTest {

	@Container
	@ServiceConnection
	private static final TiDBContainer tidb = new TiDBContainer("pingcap/tidb:v6.2.0");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select count(*) from profile");
		assertThat(records).hasSize(1);
	}

}
