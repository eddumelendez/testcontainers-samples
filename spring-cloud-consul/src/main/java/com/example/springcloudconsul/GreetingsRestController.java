package com.example.springcloudconsul;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingsRestController {

	@Value("${message}")
	private String message;

	@GetMapping("/hello")
	public String hello() {
		return this.message;
	}

}
