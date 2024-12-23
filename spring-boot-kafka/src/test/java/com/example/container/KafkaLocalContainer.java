package com.example.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class KafkaLocalContainer extends GenericContainer<KafkaLocalContainer> {

	private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

	public KafkaLocalContainer(String image) {
		super(DockerImageName.parse(image));
		withExposedPorts(9092, 8082);
		var waitStrategy = new WaitAllStrategy().withStrategy(Wait.forLogMessage(".*started.*\\n", 1))
			.withStrategy(Wait.forHttp("/").forPort(8082).forStatusCode(200));
		waitingFor(waitStrategy);
		withCreateContainerCmdModifier(cmd -> {
			cmd.withEntrypoint("sh");
		});
		withCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
	}

	@Override
	protected void containerIsStarting(InspectContainerResponse containerInfo) {
		var defaultListeners = "PLAINTEXT://localhost:29092,CONTROLLER://localhost:29093,PLAINTEXT_HOST://0.0.0.0:9092";
		var defaultAdvertisedListeners = "PLAINTEXT://localhost:29092,PLAINTEXT_HOST://%s:%d".formatted(getHost(),
				getMappedPort(9092));
		var defaultSecurityProtocolMap = "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT";

		var script = """
				#!/bin/bash
				export KAFKA_LISTENERS="%s"
				export KAFKA_ADVERTISED_LISTENERS="%s"
				export KAFKA_LISTENER_SECURITY_PROTOCOL_MAP="%s"

				/etc/confluent/docker/run
				""".formatted(defaultListeners, defaultAdvertisedListeners, defaultSecurityProtocolMap);
		copyFileToContainer(Transferable.of(script, 0777), STARTER_SCRIPT);
	}

	public String getBootstrapServer() {
		return "%s:%d".formatted(getHost(), getMappedPort(9092));
	}

	public String getRestProxyUrl() {
		return "http://%s:%d".formatted(getHost(), getMappedPort(8082));
	}

}
