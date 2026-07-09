package com.resumerank.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id parameters matching standard defaults:
        // saltLength: 16 bytes
        // hashLength: 32 bytes
        // parallelism: 1 thread
        // memory: 16384 KiB (16 MB)
        // iterations: 2
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
