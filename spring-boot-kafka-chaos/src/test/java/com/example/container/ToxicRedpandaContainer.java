package com.example.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ToxicRedpandaContainer extends RedpandaContainer {

	private List<Supplier<String>> listeners = new ArrayList<>();

	public ToxicRedpandaContainer(String image) {
		super(image);
	}

	@Override
	protected void containerIsStarting(InspectContainerResponse containerInfo) {
		String command = "#!/bin/bash\n";
		command = command + " /usr/bin/rpk redpanda start --mode dev-container --overprovisioned --smp=1";
		command = command + " --kafka-addr INTERNAL://0.0.0.0:19092,PLAINTEXT://0.0.0.0:29092,OUTSIDE://0.0.0.0:9092 ";
		Supplier<String> stringSupplier = this.listeners.get(0);
		command = command + " --advertise-kafka-addr INTERNAL://" + stringSupplier.get()
				+ ",PLAINTEXT://127.0.0.1:29092,OUTSIDE://" + this.getHost() + ":" + this.getMappedPort(9092);
		this.copyFileToContainer(Transferable.of(command, 511), "/testcontainers_start.sh");
	}

	public ToxicRedpandaContainer withAdditionalListener(Supplier<String> listener) {
		this.listeners.add(listener);
		return this;
	}

}
