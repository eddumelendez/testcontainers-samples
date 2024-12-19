package com.example.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class SpringBootKafkaStreamsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootKafkaStreamsApplication.class, args);
	}

}
