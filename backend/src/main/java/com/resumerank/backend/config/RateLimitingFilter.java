package com.resumerank.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import java.io.IOException;

public class RateLimitingFilter implements Filter {

    private final RateLimiter authRateLimiter;
    private final RateLimiter uploadRateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimiter authRateLimiter, RateLimiter uploadRateLimiter, ObjectMapper objectMapper) {
        this.authRateLimiter = authRateLimiter;
        this.uploadRateLimiter = uploadRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();
        
        // 1. Authenticated Upload/Candidate Creation Routes (Keyed by authenticated user ID alone)
        if (isUploadRateLimitedPath(path)) {
            String key = extractUserIdFromJwt(httpRequest);
            if (key == null || key.isEmpty()) {
                // If token is missing, let JwtInterceptor handle the 401 response
                chain.doFilter(httpRequest, response);
                return;
            }

            if (!uploadRateLimiter.isAllowed(key)) {
                sendRateLimitError(httpResponse, uploadRateLimiter.getRetryAfterSeconds(key));
                return;
            }
            chain.doFilter(httpRequest, response);
            return;
        }

        // 2. Unauthenticated Auth Routes (Keyed by IP + Email)
        if (isAuthRateLimitedPath(path)) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);
            String ip = getClientIp(wrappedRequest);
            String email = extractEmail(wrappedRequest);
            String key = ip + ":" + email;

            if (!authRateLimiter.isAllowed(key)) {
                sendRateLimitError(httpResponse, authRateLimiter.getRetryAfterSeconds(key));
                return;
            }
            chain.doFilter(wrappedRequest, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isAuthRateLimitedPath(String path) {
        return path.equals("/api/auth/login") ||
               path.equals("/api/auth/signup") ||
               path.equals("/api/auth/refresh") ||
               path.equals("/api/auth/reset-password/request");
    }

    private boolean isUploadRateLimitedPath(String path) {
        return path.equals("/api/uploads/signature") ||
               (path.startsWith("/api/job-postings/") && path.endsWith("/candidates"));
    }

    private String extractUserIdFromJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                return com.auth0.jwt.JWT.decode(token).getSubject();
            } catch (Exception e) {
                // Ignore decoding errors, let interceptor handle validation
            }
        }
        return null;
    }

    private void sendRateLimitError(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json; charset=UTF-8");
        
        String json = String.format(
            "{\"error\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded. Please try again later.\",\"status\":429,\"timestamp\":\"%s\"}",
            java.time.Instant.now().toString()
        );
        response.getWriter().write(json);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractEmail(CachedBodyHttpServletRequest request) {
        byte[] body = request.getCachedBody();
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
            if (node.has("email")) {
                return node.get("email").asText("");
            }
            if (node.has("refreshToken")) {
                String token = node.get("refreshToken").asText();
                return com.auth0.jwt.JWT.decode(token).getClaim("email").asString();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }
}
