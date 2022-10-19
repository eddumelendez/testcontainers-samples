package com.example.springcloudvault;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecretsRestController {

	private Environment environment;

	public SecretsRestController(Environment environment) {
		this.environment = environment;
	}

	@GetMapping("/secrets/{secret-name}")
	public String reveal(@PathVariable("secret-name") String secretName) {
		return this.environment.getProperty(secretName);
	}

}
