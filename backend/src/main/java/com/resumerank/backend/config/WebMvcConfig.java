package com.resumerank.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final String allowedOrigins;

    public WebMvcConfig(
            JwtInterceptor jwtInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.jwtInterceptor = jwtInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/job-postings/**", "/api/uploads/**", "/api/candidates/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/api/job-postings/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .maxAge(3600);
        registry.addMapping("/api/uploads/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .maxAge(3600);
        registry.addMapping("/api/candidates/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .maxAge(3600);
        registry.addMapping("/api/auth/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .maxAge(3600);
    }
}
