package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.config.JwtInterceptor;
import com.resumerank.backend.config.RateLimitConfig;
import com.resumerank.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {UploadController.class, AuthController.class},
        properties = {
                "cloudinary.cloud-name=test_cloud",
                "cloudinary.api-key=test_key",
                "cloudinary.api-secret=test_secret",
                "app.cors.allowed-origins=http://localhost:3000,https://myproductionurl.vercel.app"
        }
)
@Import({JwtInterceptor.class, RateLimitConfig.class})
class CORSAndErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private com.resumerank.backend.service.AuthService authService;

    private UUID mockUserId1;
    private UUID mockUserId2;
    private String mockToken1;
    private String mockToken2;

    @BeforeEach
    void setUp() {
        mockUserId1 = UUID.randomUUID();
        mockUserId2 = UUID.randomUUID();
        
        // Mock token must contain 3 dot-separated base64 parts to bypass com.auth0.jwt.JWT.decode verification
        mockToken1 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                org.springframework.util.Base64Utils.encodeToUrlSafeString(
                        ("{\"sub\":\"" + mockUserId1 + "\",\"email\":\"user1@example.com\",\"type\":\"access\"}").getBytes()
                ) + ".sig1";
        mockToken2 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                org.springframework.util.Base64Utils.encodeToUrlSafeString(
                        ("{\"sub\":\"" + mockUserId2 + "\",\"email\":\"user2@example.com\",\"type\":\"access\"}").getBytes()
                ) + ".sig2";

        // Mock JWT service for user 1
        DecodedJWT decodedJWT1 = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT1.getSubject()).thenReturn(mockUserId1.toString());
        Claim emailClaim1 = Mockito.mock(Claim.class);
        Mockito.when(emailClaim1.asString()).thenReturn("user1@example.com");
        Mockito.when(decodedJWT1.getClaim("email")).thenReturn(emailClaim1);
        Claim typeClaim1 = Mockito.mock(Claim.class);
        Mockito.when(typeClaim1.asString()).thenReturn("access");
        Mockito.when(decodedJWT1.getClaim("type")).thenReturn(typeClaim1);
        Mockito.when(jwtService.verifyToken(mockToken1)).thenReturn(decodedJWT1);

        // Mock JWT service for user 2
        DecodedJWT decodedJWT2 = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT2.getSubject()).thenReturn(mockUserId2.toString());
        Claim emailClaim2 = Mockito.mock(Claim.class);
        Mockito.when(emailClaim2.asString()).thenReturn("user2@example.com");
        Mockito.when(decodedJWT2.getClaim("email")).thenReturn(emailClaim2);
        Claim typeClaim2 = Mockito.mock(Claim.class);
        Mockito.when(typeClaim2.asString()).thenReturn("access");
        Mockito.when(decodedJWT2.getClaim("type")).thenReturn(typeClaim2);
        Mockito.when(jwtService.verifyToken(mockToken2)).thenReturn(decodedJWT2);
    }

    @Test
    void testConsistentErrorShapeOnValidationFailure() throws Exception {
        // Triggers validation exception due to invalid email address format
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testConsistentErrorShapeOnAuthenticationFailure() throws Exception {
        // Missing Authorization header triggers interceptor 401
        mockMvc.perform(post("/api/uploads/signature")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing or invalid Authorization header"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testCorsAllowedOriginSuccess() throws Exception {
        mockMvc.perform(options("/api/uploads/signature")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE"));
    }

    @Test
    void testCorsUnallowedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/uploads/signature")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Origin", "https://malicious.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void testUploadRateLimitingTrigger() throws Exception {
        // Make 30 successful signature requests for user 1
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/uploads/signature")
                            .header("Authorization", "Bearer " + mockToken1)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // 31st request for user 1 must fail with 429 Too Many Requests in ErrorResponse shape
        mockMvc.perform(post("/api/uploads/signature")
                        .header("Authorization", "Bearer " + mockToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded. Please try again later."))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.timestamp").exists());

        // A different user (user 2) must be completely unaffected
        mockMvc.perform(post("/api/uploads/signature")
                        .header("Authorization", "Bearer " + mockToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
