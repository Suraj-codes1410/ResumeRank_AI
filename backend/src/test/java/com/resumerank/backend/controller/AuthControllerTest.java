package com.resumerank.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerank.backend.dto.SignupRequest;
import com.resumerank.backend.dto.SignupResponse;
import com.resumerank.backend.exception.EmailAlreadyExistsException;
import com.resumerank.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void signup_Successful_Returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        SignupRequest request = new SignupRequest("user@example.com", "password123");
        SignupResponse response = new SignupResponse(userId, "user@example.com", now);

        Mockito.when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void signup_DuplicateEmail_Returns409() throws Exception {
        SignupRequest request = new SignupRequest("duplicate@example.com", "password123");

        Mockito.when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email already in use"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    @Test
    void signup_InvalidEmailFormat_Returns400() throws Exception {
        SignupRequest request = new SignupRequest("invalid-email", "password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("email: Invalid email format")));
    }

    @Test
    void signup_PasswordTooShort_Returns400() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "short");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password: Password must be at least 8 characters long")));
    }

    @Test
    void login_ValidCredentials_Returns200WithTokens() throws Exception {
        com.resumerank.backend.dto.LoginRequest request = new com.resumerank.backend.dto.LoginRequest("user@example.com", "password123");
        com.resumerank.backend.dto.LoginResponse response = new com.resumerank.backend.dto.LoginResponse("accessToken123", "refreshToken123", true);

        Mockito.when(authService.login(any(com.resumerank.backend.dto.LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken123"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken123"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void login_WrongPassword_Returns401WithGenericMessage() throws Exception {
        com.resumerank.backend.dto.LoginRequest request = new com.resumerank.backend.dto.LoginRequest("user@example.com", "wrongpassword");

        Mockito.when(authService.login(any(com.resumerank.backend.dto.LoginRequest.class)))
                .thenThrow(new com.resumerank.backend.exception.BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_NonexistentEmail_ReturnsIdentical401() throws Exception {
        com.resumerank.backend.dto.LoginRequest wrongPasswordRequest = new com.resumerank.backend.dto.LoginRequest("user@example.com", "wrongpassword");
        com.resumerank.backend.dto.LoginRequest nonexistentEmailRequest = new com.resumerank.backend.dto.LoginRequest("nonexistent@example.com", "password");

        Mockito.when(authService.login(any(com.resumerank.backend.dto.LoginRequest.class)))
                .thenThrow(new com.resumerank.backend.exception.BadCredentialsException("Invalid email or password"));

        String wrongPasswordJson = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String nonexistentEmailJson = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonexistentEmailRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(wrongPasswordJson, nonexistentEmailJson);
    }

    @Test
    void verifyEmail_ValidToken_Returns200() throws Exception {
        Mockito.doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(get("/api/auth/verify-email")
                .param("token", "valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmail_ExpiredToken_Returns400() throws Exception {
        Mockito.doThrow(new com.resumerank.backend.exception.InvalidTokenException("Token has expired"))
                .when(authService).verifyEmail("expired-token");

        mockMvc.perform(get("/api/auth/verify-email")
                .param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Token has expired"));
    }

    @Test
    void verifyEmail_UsedToken_Returns400() throws Exception {
        Mockito.doThrow(new com.resumerank.backend.exception.InvalidTokenException("Token has already been used"))
                .when(authService).verifyEmail("used-token");

        mockMvc.perform(get("/api/auth/verify-email")
                .param("token", "used-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Token has already been used"));
    }

    @Test
    void login_UnverifiedUser_ReturnsEmailVerifiedFalse() throws Exception {
        com.resumerank.backend.dto.LoginRequest request = new com.resumerank.backend.dto.LoginRequest("unverified@example.com", "password123");
        com.resumerank.backend.dto.LoginResponse response = new com.resumerank.backend.dto.LoginResponse("accessToken123", "refreshToken123", false);

        Mockito.when(authService.login(any(com.resumerank.backend.dto.LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken123"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken123"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void requestPasswordReset_Returns200() throws Exception {
        com.resumerank.backend.dto.ResetPasswordRequest request = new com.resumerank.backend.dto.ResetPasswordRequest("user@example.com");

        Mockito.doNothing().when(authService).requestPasswordReset(any(com.resumerank.backend.dto.ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void confirmPasswordReset_Successful_Returns200() throws Exception {
        com.resumerank.backend.dto.ResetPasswordConfirmRequest request = new com.resumerank.backend.dto.ResetPasswordConfirmRequest("token123", "newPassword123");

        Mockito.doNothing().when(authService).confirmPasswordReset(any(com.resumerank.backend.dto.ResetPasswordConfirmRequest.class));

        mockMvc.perform(post("/api/auth/reset-password/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void confirmPasswordReset_InvalidToken_Returns400() throws Exception {
        com.resumerank.backend.dto.ResetPasswordConfirmRequest request = new com.resumerank.backend.dto.ResetPasswordConfirmRequest("invalid-token", "newPassword123");

        Mockito.doThrow(new com.resumerank.backend.exception.InvalidTokenException("Invalid or nonexistent token"))
                .when(authService).confirmPasswordReset(any(com.resumerank.backend.dto.ResetPasswordConfirmRequest.class));

        mockMvc.perform(post("/api/auth/reset-password/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid or nonexistent token"));
    }

    @Test
    void confirmPasswordReset_WeakPassword_Returns400() throws Exception {
        com.resumerank.backend.dto.ResetPasswordConfirmRequest request = new com.resumerank.backend.dto.ResetPasswordConfirmRequest("token123", "short");

        mockMvc.perform(post("/api/auth/reset-password/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("newPassword: Password must be at least 8 characters long")));
    }
}



