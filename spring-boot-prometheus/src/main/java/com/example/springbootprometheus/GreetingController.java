package com.example.springbootprometheus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	@GetMapping("/greetings")
	public String message() {
		return "Hello World";
	}

}
