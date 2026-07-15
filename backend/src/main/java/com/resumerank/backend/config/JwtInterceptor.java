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

    private void writeError(HttpServletResponse response, HttpStatus status, String error, String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json; charset=UTF-8");
        String json = String.format(
            "{\"error\":\"%s\",\"detail\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
            error, detail, status.value(), java.time.Instant.now().toString()
        );
        response.getWriter().write(json);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            JwtService jwtService = jwtServiceProvider.getIfAvailable();
            if (jwtService == null) {
                writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Security context error: JwtService unavailable");
                return false;
            }
            
            DecodedJWT decodedJWT = jwtService.verifyToken(token);
            
            String type = decodedJWT.getClaim("type").asString();
            if (!"access".equals(type)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid token type");
                return false;
            }

            UUID userId = UUID.fromString(decodedJWT.getSubject());
            String email = decodedJWT.getClaim("email").asString();

            request.setAttribute("authenticatedUserId", userId);
            request.setAttribute("authenticatedUserEmail", email);
            return true;
        } catch (Exception e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired token");
            return false;
        }
    }
}

