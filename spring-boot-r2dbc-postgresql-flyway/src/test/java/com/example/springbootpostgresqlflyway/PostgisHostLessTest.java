package com.example.springbootpostgresqlflyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Flux;

@Testcontainers
@DataR2dbcTest(properties = {"spring.r2dbc.url=r2dbc:tc:postgresql:///?TC_IMAGE_TAG=14-3.2-alpine"})
class PostgisHostLessTest {

    @Autowired
    private R2dbcEntityTemplate r2dbcTemplate;

    @Test
    void test() {
        Flux<Profile> records = this.r2dbcTemplate.select(Profile.class).all();
        assertThat(records.collectList().block()).hasSize(1);
    }
}
