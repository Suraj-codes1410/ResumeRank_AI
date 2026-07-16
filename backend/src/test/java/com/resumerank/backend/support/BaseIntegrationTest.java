package com.resumerank.backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all backend integration tests.
 * Configures the in-memory H2 database via the "test" profile,
 * enables mock MVC configuration, and ensures all database operations
 * are rolled back after each test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {
    // Utility methods and shared testing state can be added here
}
