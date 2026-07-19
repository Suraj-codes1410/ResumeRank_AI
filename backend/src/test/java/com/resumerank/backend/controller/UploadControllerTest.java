package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.config.JwtInterceptor;
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
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UploadController.class,
        properties = {
                "cloudinary.cloud-name=test_cloud",
                "cloudinary.api-key=test_key",
                "cloudinary.api-secret=test_secret"
        }
)
@Import(JwtInterceptor.class)
@ActiveProfiles("test")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    private UUID mockUserId;
    private String mockToken;

    @BeforeEach
    void setUp() {
        mockUserId = UUID.randomUUID();
        mockToken = "validAccessToken123";
    }

    @Test
    void getUploadSignature_Authenticated_ReturnsValidSignatureAndProperties() throws Exception {
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(mockUserId.toString());

        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn("recruiter@example.com");
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(mockToken)).thenReturn(decodedJWT);

        MvcResult result = mockMvc.perform(post("/api/uploads/signature")
                        .header("Authorization", "Bearer " + mockToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.apiKey").value("test_key"))
                .andExpect(jsonPath("$.cloudName").value("test_cloud"))
                .andExpect(jsonPath("$.folder").value("resumes/" + mockUserId))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        
        // Explicitly assert that the response never contains the Cloudinary API secret
        assertFalse(responseBody.contains("test_secret"), "The response payload must never contain the Cloudinary API secret");
    }

    @Test
    void getUploadSignature_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/uploads/signature")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
