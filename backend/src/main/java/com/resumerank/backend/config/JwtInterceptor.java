package com.resumerank.backend.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.UUID;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final ObjectProvider<JwtService> jwtServiceProvider;

    public JwtInterceptor(ObjectProvider<JwtService> jwtServiceProvider) {
        this.jwtServiceProvider = jwtServiceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            JwtService jwtService = jwtServiceProvider.getIfAvailable();
            if (jwtService == null) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Security context error: JwtService unavailable");
                return false;
            }
            
            DecodedJWT decodedJWT = jwtService.verifyToken(token);
            
            String type = decodedJWT.getClaim("type").asString();
            if (!"access".equals(type)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token type");
                return false;
            }

            UUID userId = UUID.fromString(decodedJWT.getSubject());
            String email = decodedJWT.getClaim("email").asString();

            request.setAttribute("authenticatedUserId", userId);
            request.setAttribute("authenticatedUserEmail", email);
            return true;
        } catch (Exception e) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
            return false;
        }
    }
}

