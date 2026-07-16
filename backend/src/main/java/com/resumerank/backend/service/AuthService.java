package com.resumerank.backend.service;

import com.resumerank.backend.dto.LoginRequest;
import com.resumerank.backend.dto.LoginResponse;
import com.resumerank.backend.dto.ResetPasswordRequest;
import com.resumerank.backend.dto.ResetPasswordConfirmRequest;
import com.resumerank.backend.dto.SignupRequest;
import com.resumerank.backend.dto.SignupResponse;
import com.resumerank.backend.entity.EmailVerificationToken;
import com.resumerank.backend.entity.PasswordResetToken;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.exception.BadCredentialsException;
import com.resumerank.backend.exception.EmailAlreadyExistsException;
import com.resumerank.backend.exception.InvalidTokenException;
import com.resumerank.backend.repository.EmailVerificationTokenRepository;
import com.resumerank.backend.repository.PasswordResetTokenRepository;
import com.resumerank.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final String frontendUrl;

    public AuthService(UserRepository userRepository, 
                      PasswordEncoder passwordEncoder, 
                      JwtService jwtService,
                      EmailVerificationTokenRepository emailVerificationTokenRepository,
                      PasswordResetTokenRepository passwordResetTokenRepository,
                      EmailService emailService,
                      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);

        User savedUser = userRepository.saveAndFlush(user);

        // Generate email verification token (single-use, 24h expiry)
        String tokenString = generateOpaqueToken();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(tokenString);
        verificationToken.setUser(savedUser);
        verificationToken.setExpiresAt(OffsetDateTime.now().plusHours(24));
        verificationToken.setUsed(false);
        emailVerificationTokenRepository.save(verificationToken);

        // Send verification email via Resend
        String verificationLink = frontendUrl + "/verify-email?token=" + tokenString;
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationLink);

        return new SignupResponse(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        return new LoginResponse(accessToken, refreshToken, user.getEmailVerified());
    }

    @Transactional
    public void verifyEmail(String tokenString) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new InvalidTokenException("Invalid or nonexistent token"));

        if (verificationToken.getUsed()) {
            throw new InvalidTokenException("Token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);
    }

    @Transactional
    public void requestPasswordReset(ResetPasswordRequest request) {
        java.util.Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String rawToken = generateOpaqueToken();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(20)); // 20 min expiry
            resetToken.setUsed(false);
            passwordResetTokenRepository.save(resetToken);

            // Send password reset email via Resend
            String resetLink = frontendUrl + "/reset-password/confirm?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        }
    }

    @Transactional
    public void confirmPasswordReset(ResetPasswordConfirmRequest request) {
        String tokenHash = hashToken(request.token());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or nonexistent token"));

        if (resetToken.getUsed()) {
            throw new InvalidTokenException("Token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(com.resumerank.backend.dto.TokenRefreshRequest request) {
        try {
            com.auth0.jwt.interfaces.DecodedJWT decodedJWT = jwtService.verifyToken(request.refreshToken());
            String type = decodedJWT.getClaim("type").asString();
            if (!"refresh".equals(type)) {
                throw new InvalidTokenException("Invalid token type");
            }
            
            java.util.UUID userId = java.util.UUID.fromString(decodedJWT.getSubject());
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidTokenException("User not found"));
                    
            String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
            String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
            
            return new LoginResponse(newAccessToken, newRefreshToken, user.getEmailVerified());
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
    }

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}



