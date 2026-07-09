package com.resumerank.backend.service;

import com.resumerank.backend.dto.ResetPasswordConfirmRequest;
import com.resumerank.backend.dto.ResetPasswordRequest;
import com.resumerank.backend.entity.PasswordResetToken;
import com.resumerank.backend.entity.User;
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

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        emailVerificationTokenRepository = Mockito.mock(EmailVerificationTokenRepository.class);
        passwordResetTokenRepository = Mockito.mock(PasswordResetTokenRepository.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                emailVerificationTokenRepository,
                passwordResetTokenRepository
        );
    }

    @Test
    void requestPasswordReset_ExistingEmail_GeneratesTokenRecord() {
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
        Assertions.assertNotNull(savedToken.getTokenHash());
        Assertions.assertFalse(savedToken.getUsed());
        Assertions.assertTrue(savedToken.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void requestPasswordReset_NonexistentEmail_DoesNotGenerateTokenRecord() {
        Mockito.when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest("nonexistent@example.com");
        authService.requestPasswordReset(request);

        Mockito.verify(passwordResetTokenRepository, Mockito.never()).save(Mockito.any());
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

        // Raw token "dummyRawToken" will match the mocked hash call in the test setup
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
        token.setExpiresAt(OffsetDateTime.now().minusMinutes(5)); // expired
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
        token.setUsed(true); // already used

        Mockito.when(passwordResetTokenRepository.findByTokenHash(Mockito.anyString())).thenReturn(Optional.of(token));

        ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("dummyRawToken", "newPassword123");

        InvalidTokenException exception = Assertions.assertThrows(
                InvalidTokenException.class,
                () -> authService.confirmPasswordReset(request)
        );
        Assertions.assertEquals("Token has already been used", exception.getMessage());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    }
}
