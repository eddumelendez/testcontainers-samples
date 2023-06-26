package com.example.sqlserverdemo;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MSSQLServerContainer;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Junit4Test {

	@ClassRule
	public static MSSQLServerContainer sqlserver = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2017-CU12")
		.acceptLicense();

	@DynamicPropertySource
	static void sqlserverProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", sqlserver::getJdbcUrl);
		registry.add("spring.datasource.username", sqlserver::getUsername);
		registry.add("spring.datasource.password", sqlserver::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	public void contextLoads() {
		this.jdbcTemplate.update("insert into profile (name) values ('profile-1')");
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
