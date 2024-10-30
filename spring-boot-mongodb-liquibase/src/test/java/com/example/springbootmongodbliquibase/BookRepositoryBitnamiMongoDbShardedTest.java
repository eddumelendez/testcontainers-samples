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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class BookRepositoryBitnamiMongoDbShardedTest {

	static Network network = Network.newNetwork();

	private static final GenericContainer<?> mongoConfigSvr = new GenericContainer<>("bitnami/mongodb-sharded:8.0.3")
		.withExposedPorts(27017)
		.withEnv("MONGODB_SHARDING_MODE", "configsvr")
		.withEnv("MONGODB_REPLICA_SET_NAME", "tc-rs")
		.withEnv("MONGODB_REPLICA_SET_MODE", "primary")
		.withEnv("MONGODB_REPLICA_SET_KEY", "testcontainers")
		.withEnv("MONGODB_ROOT_PASSWORD", "test")
		.withEnv("MONGODB_ADVERTISED_HOSTNAME", "configsvr")
		.withNetwork(network)
		.withNetworkAliases("configsvr")
		.waitingFor(Wait.forLogMessage(".*mongod startup complete.*", 1));

	private static final GenericContainer<?> mongos = new GenericContainer<>("bitnami/mongodb-sharded:8.0.3")
		.withExposedPorts(27017)
		.withEnv("MONGODB_SHARDING_MODE", "mongos")
		.withEnv("MONGODB_CFG_PRIMARY_HOST", "configsvr")
		.withEnv("MONGODB_CFG_REPLICA_SET_NAME", "tc-rs")
		.withEnv("MONGODB_REPLICA_SET_KEY", "testcontainers")
		.withEnv("MONGODB_ROOT_PASSWORD", "test")
		.withEnv("MONGODB_ADVERTISED_HOSTNAME", "mongos")
		.withNetwork(network)
		.withNetworkAliases("mongos")
		.waitingFor(Wait.forLogMessage(".*mongos startup complete.*", 1))
		.dependsOn(mongoConfigSvr);

	@Container
	private static final GenericContainer<?> mongoShardSvr = new GenericContainer<>("bitnami/mongodb-sharded:8.0.3")
		.withExposedPorts(27017)
		.withEnv("MONGODB_DATABASE", "test")
		.withEnv("MONGODB_SHARDING_MODE", "shardsvr")
		.withEnv("MONGODB_REPLICA_SET_MODE", "primary")
		.withEnv("MONGODB_MONGOS_HOST", "mongos")
		.withEnv("MONGODB_REPLICA_SET_NAME", "shard0")
		.withEnv("MONGODB_REPLICA_SET_KEY", "testcontainers")
		.withEnv("MONGODB_ROOT_PASSWORD", "test")
		.withEnv("MONGODB_ADVERTISED_HOSTNAME", "shardsvr")
		.withNetwork(network)
		.withNetworkAliases("shardsvr")
		.waitingFor(Wait.forLogMessage(".*Replica set primary server change detected.*", 1))
		.dependsOn(mongoConfigSvr, mongos);

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", () -> "mongodb://root:test@%s:%d/test?authSource=admin"
			.formatted(mongos.getHost(), mongos.getMappedPort(27017)));
	}

	@Autowired
	private PersonRepository repository;

	@Test
	void test() throws LiquibaseException, IOException, InterruptedException {
		String stdout = mongos
			.execInContainer("mongosh --username root --password test --eval \"sh.status()\"".split(" "))
			.getStdout();
		assertThat(stdout).contains("shards");

		var database = (MongoLiquibaseDatabase) DatabaseFactory.getInstance()
			.openDatabase(String.format("mongodb://%s:%d/test?authSource=admin", mongos.getHost(),
					mongos.getMappedPort(27017)), "root", "test", null, null);
		var liquibase = new Liquibase("db/changelog/db.changelog-master.json", new ClassLoaderResourceAccessor(),
				database);
		liquibase.update("");

		var books = this.repository.findAll();
		assertThat(books).hasSize(3);
	}

}
