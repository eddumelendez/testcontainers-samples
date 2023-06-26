package com.example.springcloudzookeeper;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SpringCloudZookeeperApplicationTests {

	private static final int ZOOKEEPER_PORT = 2181;

	@Container
	private static final GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.8.0")
		.withExposedPorts(ZOOKEEPER_PORT);

	@Autowired
	private Environment environment;

	@BeforeAll
	static void beforeAll() throws Exception {
		var zkConnectionString = "%s:%d".formatted(zookeeper.getHost(), zookeeper.getMappedPort(ZOOKEEPER_PORT));
		System.setProperty("spring.config.import", "zookeeper:%s/messages".formatted(zkConnectionString));

		var curatorFramework = CuratorFrameworkFactory.builder()
			.connectString(zkConnectionString)
			.retryPolicy(new RetryOneTime(100))
			.build();
		curatorFramework.start();
		curatorFramework.create()
			.creatingParentsIfNeeded()
			.forPath("/messages/zk-tc", "Running Zookeeper with Testcontainers".getBytes());
		curatorFramework.close();
	}

	@Test
	void contextLoads() {
		assertThat(this.environment.containsProperty("zk-tc")).isTrue();
		assertThat(this.environment.getProperty("zk-tc")).isEqualTo("Running Zookeeper with Testcontainers");
	}

}
