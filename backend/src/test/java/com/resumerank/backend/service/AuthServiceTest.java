package com.resumerank.backend.service;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.dto.LoginRequest;
import com.resumerank.backend.dto.LoginResponse;
import com.resumerank.backend.dto.ResetPasswordConfirmRequest;
import com.resumerank.backend.dto.ResetPasswordRequest;
import com.resumerank.backend.dto.SignupRequest;
import com.resumerank.backend.dto.SignupResponse;
import com.resumerank.backend.dto.TokenRefreshRequest;
import com.resumerank.backend.entity.EmailVerificationToken;
import com.resumerank.backend.entity.PasswordResetToken;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.exception.BadCredentialsException;
import com.resumerank.backend.exception.EmailAlreadyExistsException;
import com.resumerank.backend.exception.InvalidTokenException;
import com.resumerank.backend.repository.EmailVerificationTokenRepository;
import com.resumerank.backend.repository.PasswordResetTokenRepository;
import com.resumerank.backend.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailService emailService;
    private AuthService authService;

    private static final String FRONTEND_URL = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        emailVerificationTokenRepository = Mockito.mock(EmailVerificationTokenRepository.class);
        passwordResetTokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
        emailService = Mockito.mock(EmailService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                emailVerificationTokenRepository,
                passwordResetTokenRepository,
                emailService,
                FRONTEND_URL
        );
    }

    @Test
    void signup_Success_SendsVerificationEmailWithCorrectRecipientAndLink() {
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("newuser@example.com");
        savedUser.setCreatedAt(OffsetDateTime.now());

        Mockito.when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        Mockito.when(passwordEncoder.encode("securePass123")).thenReturn("hashedPassword");
        Mockito.when(userRepository.saveAndFlush(Mockito.any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest("newuser@example.com", "securePass123");
        SignupResponse response = authService.signup(request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("newuser@example.com", response.email());

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        Mockito.verify(emailVerificationTokenRepository, Mockito.times(1)).save(tokenCaptor.capture());
        EmailVerificationToken savedToken = tokenCaptor.getValue();
        Assertions.assertNotNull(savedToken.getToken());
        Assertions.assertFalse(savedToken.getUsed());

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService, Mockito.times(1))
                .sendVerificationEmail(emailCaptor.capture(), linkCaptor.capture());

        Assertions.assertEquals("newuser@example.com", emailCaptor.getValue());
        Assertions.assertTrue(linkCaptor.getValue().startsWith(FRONTEND_URL + "/verify-email?token="));
        String tokenInLink = linkCaptor.getValue().replace(FRONTEND_URL + "/verify-email?token=", "");
        Assertions.assertEquals(savedToken.getToken(), tokenInLink);
    }

    @Test
    void signup_DuplicateEmail_ThrowsEmailAlreadyExistsException() {
        Mockito.when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        SignupRequest request = new SignupRequest("duplicate@example.com", "securePass123");
        Assertions.assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.signup(request)
        );
    }

    @Test
    void login_Success_ReturnsTokensAndVerifiedStatus() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashedPassword");
        user.setEmailVerified(true);

        Mockito.when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        Mockito.when(jwtService.generateAccessToken(userId, "user@example.com")).thenReturn("access_token");
        Mockito.when(jwtService.generateRefreshToken(userId, "user@example.com")).thenReturn("refresh_token");

        LoginRequest request = new LoginRequest("user@example.com", "password123");
        LoginResponse response = authService.login(request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("access_token", response.accessToken());
        Assertions.assertEquals("refresh_token", response.refreshToken());
        Assertions.assertTrue(response.emailVerified());
    }

    @Test
    void login_WrongPassword_ThrowsBadCredentialsException() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashedPassword");

        Mockito.when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        Mockito.when(passwordEncoder.matches("wrong_password", "hashedPassword")).thenReturn(false);

        LoginRequest request = new LoginRequest("user@example.com", "wrong_password");
        Assertions.assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void login_NonexistentEmail_ThrowsBadCredentialsException() {
        Mockito.when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");
        Assertions.assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void verifyEmail_Success_UpdatesUserAndTokenStatus() {
        User user = new User();
        user.setEmailVerified(false);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setExpiresAt(OffsetDateTime.now().plusHours(10));
        token.setUsed(false);

        Mockito.when(emailVerificationTokenRepository.findByToken("valid_token")).thenReturn(Optional.of(token));

        authService.verifyEmail("valid_token");

        Assertions.assertTrue(user.getEmailVerified());
        Assertions.assertTrue(token.getUsed());
        Mockito.verify(userRepository, Mockito.times(1)).save(user);
        Mockito.verify(emailVerificationTokenRepository, Mockito.times(1)).save(token);
    }

    @Test
    void verifyEmail_TokenNotFound_ThrowsInvalidTokenException() {
        Mockito.when(emailVerificationTokenRepository.findByToken("nonexistent_token")).thenReturn(Optional.empty());

        Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.verifyEmail("nonexistent_token")
        );
    }

    @Test
    void verifyEmail_AlreadyUsed_ThrowsInvalidTokenException() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUsed(true);

        Mockito.when(emailVerificationTokenRepository.findByToken("used_token")).thenReturn(Optional.of(token));

        Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.verifyEmail("used_token")
        );
    }

    @Test
    void verifyEmail_Expired_ThrowsInvalidTokenException() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now().minusHours(1)); // Expired 1 hour ago

        Mockito.when(emailVerificationTokenRepository.findByToken("expired_token")).thenReturn(Optional.of(token));

        Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.verifyEmail("expired_token")
        );
    }

    @Test
    void requestPasswordReset_ExistingEmail_SendsResetEmailWithCorrectRecipientAndLink() {
        User user = new User();
        user.setEmail("user@example.com");

        Mockito.when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com");
        authService.requestPasswordReset(request);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        Mockito.verify(passwordResetTokenRepository, Mockito.times(1)).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        Assertions.assertNotNull(savedToken);
        Assertions.assertEquals(user, savedToken.getUser());
        Assertions.assertFalse(savedToken.getUsed());
        Assertions.assertTrue(savedToken.getExpiresAt().isAfter(OffsetDateTime.now()));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService, Mockito.times(1))
                .sendPasswordResetEmail(emailCaptor.capture(), linkCaptor.capture());

        Assertions.assertEquals("user@example.com", emailCaptor.getValue());
        Assertions.assertTrue(linkCaptor.getValue().startsWith(FRONTEND_URL + "/reset-password/confirm?token="));
    }

    @Test
    void requestPasswordReset_NonexistentEmail_DoesNotSendEmail() {
        Mockito.when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest("nonexistent@example.com");
        authService.requestPasswordReset(request);

        Mockito.verify(passwordResetTokenRepository, Mockito.never()).save(Mockito.any());
        Mockito.verify(emailService, Mockito.never()).sendPasswordResetEmail(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void confirmPasswordReset_ValidToken_UpdatesPasswordAndInvalidatesToken() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("oldHash");

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("dummyHash");
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        token.setUsed(false);

        Mockito.when(passwordResetTokenRepository.findByTokenHash(Mockito.anyString())).thenReturn(Optional.of(token));
        Mockito.when(passwordEncoder.encode("newPassword123")).thenReturn("newHash");

        ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("dummyRawToken", "newPassword123");
        authService.confirmPasswordReset(request);

        Assertions.assertEquals("newHash", user.getPasswordHash());
        Assertions.assertTrue(token.getUsed());
        Mockito.verify(userRepository, Mockito.times(1)).save(user);
        Mockito.verify(passwordResetTokenRepository, Mockito.times(1)).save(token);
    }

    @Test
    void confirmPasswordReset_ExpiredToken_ThrowsException() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiresAt(OffsetDateTime.now().minusMinutes(5));
        token.setUsed(false);

        Mockito.when(passwordResetTokenRepository.findByTokenHash(Mockito.anyString())).thenReturn(Optional.of(token));

        ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("dummyRawToken", "newPassword123");

        InvalidTokenException exception = Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.confirmPasswordReset(request)
        );
        Assertions.assertEquals("Token has expired", exception.getMessage());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void confirmPasswordReset_UsedToken_ThrowsException() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        token.setUsed(true);

        Mockito.when(passwordResetTokenRepository.findByTokenHash(Mockito.anyString())).thenReturn(Optional.of(token));

        ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("dummyRawToken", "newPassword123");

        InvalidTokenException exception = Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.confirmPasswordReset(request)
        );
        Assertions.assertEquals("Token has already been used", exception.getMessage());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void refresh_Success_ReturnsNewTokens() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setEmailVerified(true);

        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("refresh");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        Mockito.when(decodedJWT.getSubject()).thenReturn(userId.toString());

        Mockito.when(jwtService.verifyToken("refresh_token_123")).thenReturn(decodedJWT);
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(jwtService.generateAccessToken(userId, "user@example.com")).thenReturn("new_access_token");
        Mockito.when(jwtService.generateRefreshToken(userId, "user@example.com")).thenReturn("new_refresh_token");

        TokenRefreshRequest request = new TokenRefreshRequest("refresh_token_123");
        LoginResponse response = authService.refresh(request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("new_access_token", response.accessToken());
        Assertions.assertEquals("new_refresh_token", response.refreshToken());
        Assertions.assertTrue(response.emailVerified());
    }

    @Test
    void refresh_InvalidTokenType_ThrowsInvalidTokenException() {
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access"); // Expected refresh
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken("access_token_instead")).thenReturn(decodedJWT);

        TokenRefreshRequest request = new TokenRefreshRequest("access_token_instead");
        Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.refresh(request)
        );
    }
}
