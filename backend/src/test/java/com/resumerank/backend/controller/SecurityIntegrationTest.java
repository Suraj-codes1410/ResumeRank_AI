package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @Test
    void protectedEndpoint_MissingAuthorizationHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/job-postings")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing or invalid Authorization header"));
    }

    @Test
    void protectedEndpoint_InvalidTokenFormat_Returns401() throws Exception {
        mockMvc.perform(get("/api/job-postings")
                .header("Authorization", "InvalidHeaderFormat")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing or invalid Authorization header"));
    }

    @Test
    void protectedEndpoint_ExpiredOrInvalidToken_Returns401() throws Exception {
        Mockito.when(jwtService.verifyToken("bad_token")).thenThrow(new RuntimeException("Token expired"));

        mockMvc.perform(get("/api/job-postings")
                .header("Authorization", "Bearer bad_token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    void protectedEndpoint_WrongTokenType_Returns401() throws Exception {
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("refresh"); // Expected "access"
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        
        Mockito.when(jwtService.verifyToken("refresh_token_used")).thenReturn(decodedJWT);

        mockMvc.perform(get("/api/job-postings")
                .header("Authorization", "Bearer refresh_token_used")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid token type"));
    }

    @Test
    void protectedEndpoint_ValidAccessToken_PassesSecurityInterceptor() throws Exception {
        UUID userId = UUID.randomUUID();
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Claim emailClaim = Mockito.mock(Claim.class);
        
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(emailClaim.asString()).thenReturn("user@example.com");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        Mockito.when(decodedJWT.getSubject()).thenReturn(userId.toString());
        
        Mockito.when(jwtService.verifyToken("valid_access_token")).thenReturn(decodedJWT);

        // Since the user exists (or we check the mock db), the interceptor passes and it reaches the controller.
        // It returns 200 or 404 (due to empty list/no mock database entries setup, but NOT a 401 Security Block).
        mockMvc.perform(get("/api/job-postings")
                .header("Authorization", "Bearer valid_access_token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Returns 200 list (empty)
    }
}
