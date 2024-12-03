package com.example.springboothazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringBootHazelcastApplicationTest {

	@Container
	@ServiceConnection
	private static final GenericContainer<?> hazelcast = new GenericContainer<>("hazelcast/hazelcast:5.5-jdk21")
		.withExposedPorts(5701);

	@Autowired
	private HazelcastInstance hazelcastInstance;

	@Test
	void test() {
		var mapping = """
				CREATE MAPPING cities (
				__key INT,
				countries VARCHAR,
				cities VARCHAR)
				TYPE IMap
				OPTIONS('keyFormat'='int', 'valueFormat'='json-flat');
				""";
		var insert = """
				INSERT INTO cities VALUES
				(1, 'United Kingdom','London'),
				(2, 'United Kingdom','Manchester'),
				(3, 'United States', 'New York'),
				(4, 'United States', 'Los Angeles'),
				(5, 'Turkey', 'Ankara'),
				(6, 'Turkey', 'Istanbul'),
				(7, 'Brazil', 'Sao Paulo'),
				(8, 'Brazil', 'Rio de Janeiro');
				""";

		SqlService sql = this.hazelcastInstance.getSql();
		try (SqlResult mappingResult = sql.execute(mapping);
				SqlResult insertResult = sql.execute(insert);
				SqlResult sqlResult = sql.execute("SELECT cities FROM cities")) {
			assertThat(sqlResult).extracting(row -> row.getObject("cities"))
				.containsExactlyInAnyOrder("London", "Manchester", "New York", "Los Angeles", "Ankara", "Istanbul",
						"Sao Paulo", "Rio de Janeiro");
		}
	}

}