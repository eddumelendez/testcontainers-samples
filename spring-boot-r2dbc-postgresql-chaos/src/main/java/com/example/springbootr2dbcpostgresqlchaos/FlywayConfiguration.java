package com.example.springbootr2dbcpostgresqlchaos;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfiguration {

	@Bean(initMethod = "migrate")
	Flyway flyway(FlywayProperties properties) {
		String[] locations = new String[properties.getLocations().size()];
		properties.getLocations().toArray(locations);
		return new Flyway(Flyway.configure().baselineOnMigrate(true).locations(locations)
				.defaultSchema(properties.getDefaultSchema())
				.dataSource(properties.getUrl(), properties.getUser(), properties.getPassword()));
	}

}
