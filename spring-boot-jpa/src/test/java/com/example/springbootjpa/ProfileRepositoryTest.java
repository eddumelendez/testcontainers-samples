package com.example.springbootjpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.generate-ddl=true")
class ProfileRepositoryTest {

	@Container
	@ServiceConnection
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

	@Autowired
	private ProfileRepository repository;

	@Test
	void test() {
		this.repository.saveAndFlush(new Profile(UUID.randomUUID().toString(), "Eddú"));

		assertThat(this.repository.count()).isEqualTo(1);
	}

}