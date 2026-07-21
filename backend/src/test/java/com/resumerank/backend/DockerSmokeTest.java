package com.resumerank.backend;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DockerSmokeTest {

    @Test
    void dockerWorks() {
        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            System.out.println(postgres.getJdbcUrl());
        }
    }
}