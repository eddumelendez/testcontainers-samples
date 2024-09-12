package com.example.springbootpostgresqlflyway;

import io.synthesized.tdktc.SynthesizedTDK;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
class PostgresTest {

	private static Network network = Network.newNetwork();

	@Container
	private static final PostgreSQLContainer<?> postgresIn = new PostgreSQLContainer<>("postgres:15-alpine")
		.withNetwork(network);

	@Container
	private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
		.withNetwork(network);

	private static final String synthesizedConfig = """
			default_config:
			  mode: "GENERATION"
			  target_row_number: 10
			tables:
			  - table_name_with_schema: "public.flyway_schema_history"
			    mode: "KEEP"
			global_seed: 42
			""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.flyway.url", postgresIn::getJdbcUrl);
		registry.add("spring.flyway.user", postgresIn::getUsername);
		registry.add("spring.flyway.password", postgresIn::getPassword);
	}

	@Test
	void test() {
		var records = this.jdbcTemplate.queryForList("select * from profile");
		assertThat(records).hasSize(10);
	}

	@TestConfiguration
	@DependsOnDatabaseInitialization
	static class Config {

		Config() {
			new SynthesizedTDK().transform(postgresIn, postgres, synthesizedConfig);
		}

	}

}
