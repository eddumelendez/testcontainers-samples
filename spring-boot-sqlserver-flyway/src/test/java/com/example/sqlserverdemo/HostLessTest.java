package com.example.sqlserverdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJdbcTest(properties = { "spring.datasource.url=jdbc:tc:sqlserver:2022-CU14-ubuntu-22.04:///" })
class HostLessTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		this.jdbcTemplate.update("insert into profile (name) values ('profile-1')");
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
