package com.resumerank.backend.service;

import com.resumerank.backend.dto.LoginRequest;
import com.resumerank.backend.dto.LoginResponse;
import com.resumerank.backend.dto.SignupRequest;
import com.resumerank.backend.dto.SignupResponse;
import com.resumerank.backend.entity.EmailVerificationToken;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.exception.BadCredentialsException;
import com.resumerank.backend.exception.EmailAlreadyExistsException;
import com.resumerank.backend.exception.InvalidTokenException;
import com.resumerank.backend.repository.EmailVerificationTokenRepository;
import com.resumerank.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.security.SecureRandom;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    public AuthService(UserRepository userRepository, 
                      PasswordEncoder passwordEncoder, 
                      JwtService jwtService,
                      EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
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

        // Log the verification link to console output (actual sending is out of scope for now)
        String verificationLink = "http://localhost:8080/api/auth/verify-email?token=" + tokenString;
        System.out.println("Verification link for user " + savedUser.getEmail() + ": " + verificationLink);

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

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}


