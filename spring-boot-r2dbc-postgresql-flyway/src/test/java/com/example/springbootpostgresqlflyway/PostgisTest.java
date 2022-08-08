package com.example.springbootpostgresqlflyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import reactor.core.publisher.Flux;

@Testcontainers
@DataR2dbcTest
class PostgisTest {

    private static final DockerImageName dockerImageName = DockerImageName.parse("postgis/postgis")
            .withTag("14-3.2-alpine")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(dockerImageName);

    @Autowired
    private R2dbcEntityTemplate r2dbcTemplate;

    @DynamicPropertySource
    static void sqlserverProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:tc:postgresql:///?TC_IMAGE_TAG=14-3.2-alpine");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Test
    void test() {
        Flux<Profile> records = this.r2dbcTemplate.select(Profile.class).all();
        assertThat(records.collectList().block()).hasSize(1);
    }

}
