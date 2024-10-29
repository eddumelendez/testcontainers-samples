package com.example.springbootmongodbliquibase;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class BookRepositoryBitnamiMongoDbReplicaSetTest {

	@Container
	private static final GenericContainer<?> mongo = new GenericContainer<>("bitnami/mongodb:8.0.3")
		.withExposedPorts(27017)
		.withEnv("MONGODB_DATABASE", "test")
		.withEnv("MONGODB_REPLICA_SET_MODE", "primary")
		.withEnv("ALLOW_EMPTY_PASSWORD", "yes")
		.waitingFor(Wait.forLogMessage(".*mongod startup complete.*", 1));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri",
				() -> String.format("mongodb://%s:%d/test", mongo.getHost(), mongo.getMappedPort(27017)));
	}

	@Autowired
	private PersonRepository repository;

	@Test
	void test() throws LiquibaseException, IOException, InterruptedException {
		String stdout = mongo.execInContainer("mongosh --eval \"printjson(db.isMaster())\"".split(" ")).getStdout();
		assertThat(stdout).contains("setName");

		var database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance()
			.openDatabase(String.format("mongodb://%s:%d/test", mongo.getHost(), mongo.getMappedPort(27017)), null,
					null, null, null);
		var liquibase = new Liquibase("db/changelog/db.changelog-master.json", new ClassLoaderResourceAccessor(),
				database);
		liquibase.update("");

		var books = this.repository.findAll();
		assertThat(books).hasSize(3);
	}

}
