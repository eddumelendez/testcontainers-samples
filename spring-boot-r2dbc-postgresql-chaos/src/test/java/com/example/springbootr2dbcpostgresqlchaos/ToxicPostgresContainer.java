package com.example.springbootr2dbcpostgresqlchaos;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;

public class ToxicPostgresContainer extends ToxiproxyContainer {

	public ToxicPostgresContainer() {
		super("ghcr.io/shopify/toxiproxy:2.8.0");

		Network network = Network.newNetwork();

		PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine").withNetwork(network)
			.withNetworkAliases("postgres");
		withNetwork(network);
		dependsOn(postgres);
	}

}
