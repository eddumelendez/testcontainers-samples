package org.example.springbootoracle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringBootOracleApplicationTests {

	@Container
	@ServiceConnection
	static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.5-slim-faststart");

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void test() {
		var records = this.jdbcClient.sql("select * from profile").query().listOfRows();
		assertThat(records).hasSize(1);
	}

}
