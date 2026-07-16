package com.resumerank.backend.service;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "dGhpcy1pcy1hLXN1cGVyLXNlY3JldC1rZXktdGhhdC1pcy1hdC1sZWFzdC0yNTYtYml0cy1sb25nLWZvci1obWFj";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret);
    }

    @Test
    void generateAccessToken_GeneratesValidTokenWithCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        String token = jwtService.generateAccessToken(userId, email);
        Assertions.assertNotNull(token);

        DecodedJWT decoded = jwtService.verifyToken(token);
        Assertions.assertEquals(userId.toString(), decoded.getSubject());
        Assertions.assertEquals(email, decoded.getClaim("email").asString());
        Assertions.assertEquals("access", decoded.getClaim("type").asString());
        Assertions.assertNotNull(decoded.getExpiresAt());
    }

    @Test
    void generateRefreshToken_GeneratesValidTokenWithCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        String token = jwtService.generateRefreshToken(userId, email);
        Assertions.assertNotNull(token);

        DecodedJWT decoded = jwtService.verifyToken(token);
        Assertions.assertEquals(userId.toString(), decoded.getSubject());
        Assertions.assertEquals(email, decoded.getClaim("email").asString());
        Assertions.assertEquals("refresh", decoded.getClaim("type").asString());
        Assertions.assertNotNull(decoded.getExpiresAt());
    }

    @Test
    void verifyToken_InvalidToken_ThrowsJWTDecodeException() {
        Assertions.assertThrows(
                JWTDecodeException.class,
                () -> jwtService.verifyToken("invalid.token.here")
        );
    }

    @Test
    void verifyToken_WrongSignature_ThrowsSignatureVerificationException() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        // Generate token with different service/secret
        JwtService otherService = new JwtService("different-secret-different-secret-different-secret");
        String badSignatureToken = otherService.generateAccessToken(userId, email);

        Assertions.assertThrows(
                SignatureVerificationException.class,
                () -> jwtService.verifyToken(badSignatureToken)
        );
    }
}
