package com.example.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ToxicKafkaRaftContainer extends KafkaContainer {

	private List<Supplier<String>> listeners = new ArrayList<>();

	public ToxicKafkaRaftContainer(String image) {
		super(DockerImageName.parse(image));
	}

	@Override
	protected void configure() {
		super.configure();
		withEnv("KAFKA_LISTENERS",
				String.format("%s,%s", "INTERNAL://0.0.0.0:19092", getEnvMap().get("KAFKA_LISTENERS")));
		withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
				String.format("%s,%s", "INTERNAL:PLAINTEXT", getEnvMap().get("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP")));
	}

	@Override
	protected void containerIsStarting(InspectContainerResponse containerInfo) {
		String command = "#!/bin/bash\n";
		// exporting KAFKA_ADVERTISED_LISTENERS with the container hostname
		command += String.format("export KAFKA_ADVERTISED_LISTENERS=%s,%s,%s\n",
				String.format("INTERNAL://%s", listeners.get(0).get()), getBootstrapServers(),
				brokerAdvertisedListener(containerInfo));

		// Uncomment the lines below if testing with versions less than 7.4.0
		// command += "echo '' > /etc/confluent/docker/ensure \n";
		// command += commandKraft();

		command += "/etc/confluent/docker/run \n";
		copyFileToContainer(Transferable.of(command, 0777), "/testcontainers_start.sh");
	}

	public ToxicKafkaRaftContainer withAdditionalListener(Supplier<String> listener) {
		this.listeners.add(listener);
		return this;
	}

}
