package com.example.springbootmongodbliquibase;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class BookRepositoryTest {

	@Container
	@ServiceConnection
	private static final MongoDBContainer mongo = new MongoDBContainer("mongo:6");

	@Autowired
	private PersonRepository repository;

	@Test
	void test() throws LiquibaseException {
		var database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance()
			.openDatabase(mongo.getReplicaSetUrl("test"), null, null, null, null);
		var liquibase = new Liquibase("db/changelog/db.changelog-master.json", new ClassLoaderResourceAccessor(),
				database);
		liquibase.update("");

		var books = this.repository.findAll();
		assertThat(books).hasSize(3);
	}

}
