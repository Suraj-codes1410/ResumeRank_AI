package com.resumerank.backend.config;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

class JwtInterceptorTest {

    private JwtService jwtService;
    private ObjectProvider<JwtService> jwtServiceProvider;
    private JwtInterceptor jwtInterceptor;
    
    private HttpServletRequest request;
    private HttpServletResponse response;
    
    private StringWriter responseWriter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jwtService = Mockito.mock(JwtService.class);
        jwtServiceProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(jwtServiceProvider.getIfAvailable()).thenReturn(jwtService);
        
        jwtInterceptor = new JwtInterceptor(jwtServiceProvider);
        
        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        
        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        Mockito.when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    void preHandle_OptionsRequest_ReturnsTrue() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("OPTIONS");
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertTrue(result);
        Mockito.verifyNoInteractions(jwtService);
    }

    @Test
    void preHandle_MissingHeader_ReturnsFalseAndWrites401() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getHeader("Authorization")).thenReturn(null);
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertFalse(result);
        Mockito.verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        Assertions.assertTrue(responseWriter.toString().contains("Missing or invalid Authorization header"));
    }

    @Test
    void preHandle_InvalidHeaderFormat_ReturnsFalseAndWrites401() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Token token123");
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertFalse(result);
        Mockito.verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        Assertions.assertTrue(responseWriter.toString().contains("Missing or invalid Authorization header"));
    }

    @Test
    void preHandle_JwtServiceUnavailable_ReturnsFalseAndWrites500() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer token123");
        Mockito.when(jwtServiceProvider.getIfAvailable()).thenReturn(null);
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertFalse(result);
        Mockito.verify(response).setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        Assertions.assertTrue(responseWriter.toString().contains("Security context error: JwtService unavailable"));
    }

    @Test
    void preHandle_InvalidTokenType_ReturnsFalseAndWrites401() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("POST");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer refresh_token_123");
        
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("refresh");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        Mockito.when(jwtService.verifyToken("refresh_token_123")).thenReturn(decodedJWT);
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertFalse(result);
        Mockito.verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        Assertions.assertTrue(responseWriter.toString().contains("Invalid token type"));
    }

    @Test
    void preHandle_ExpiredOrInvalidToken_ReturnsFalseAndWrites401() throws Exception {
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer expired_token");
        Mockito.when(jwtService.verifyToken("expired_token")).thenThrow(new RuntimeException("Expired"));
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertFalse(result);
        Mockito.verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        Assertions.assertTrue(responseWriter.toString().contains("Invalid or expired token"));
    }

    @Test
    void preHandle_ValidAccessToken_SetsAttributesAndReturnsTrue() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "recruiter@example.com";
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer valid_token");
        
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Claim emailClaim = Mockito.mock(Claim.class);
        
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(emailClaim.asString()).thenReturn(email);
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        Mockito.when(decodedJWT.getSubject()).thenReturn(userId.toString());
        Mockito.when(jwtService.verifyToken("valid_token")).thenReturn(decodedJWT);
        
        boolean result = jwtInterceptor.preHandle(request, response, new Object());
        
        Assertions.assertTrue(result);
        Mockito.verify(request).setAttribute("authenticatedUserId", userId);
        Mockito.verify(request).setAttribute("authenticatedUserEmail", email);
    }
}
