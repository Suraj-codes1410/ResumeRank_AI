package com.resumerank.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter loginRateLimiter() {
        // 5 attempts per 15 minutes (900 seconds)
        return new RateLimiter(5, 900);
    }

    @Bean
    public RateLimiter uploadRateLimiter() {
        // 30 uploads/signatures per 15 minutes (900 seconds)
        return new RateLimiter(30, 900);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
            RateLimiter loginRateLimiter, RateLimiter uploadRateLimiter, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitingFilter(loginRateLimiter, uploadRateLimiter, objectMapper));
        registration.addUrlPatterns(
                "/api/auth/login",
                "/api/auth/signup",
                "/api/auth/refresh",
                "/api/auth/reset-password/request",
                "/api/uploads/signature",
                "/api/job-postings/*/candidates"
        );
        registration.setName("rateLimitingFilter");
        registration.setOrder(1); // Set high precedence
        return registration;
    }
}
