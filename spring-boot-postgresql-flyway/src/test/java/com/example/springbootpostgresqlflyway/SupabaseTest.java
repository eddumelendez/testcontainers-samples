package com.example.springbootpostgresqlflyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
class SupabaseTest {

	private static final DockerImageName dockerImageName = DockerImageName.parse("supabase/postgres")
		.withTag("15.1.0.76")
		.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(dockerImageName)
		.withUsername("postgres")
		.withCommand("postgres", "-c", "config_file=/etc/postgresql/postgresql.conf");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(1);
	}

}
