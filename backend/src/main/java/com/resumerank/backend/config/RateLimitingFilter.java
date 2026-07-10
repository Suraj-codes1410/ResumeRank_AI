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
import org.springframework.http.MediaType;
import java.io.IOException;

public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
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
        if (isRateLimitedPath(path)) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);
            String ip = getClientIp(wrappedRequest);
            String email = extractEmail(wrappedRequest);
            String key = ip + ":" + email;

            if (!rateLimiter.isAllowed(key)) {
                long retryAfter = rateLimiter.getRetryAfterSeconds(key);
                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setHeader("Retry-After", String.valueOf(retryAfter));
                httpResponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                
                org.springframework.http.ProblemDetail problemDetail = org.springframework.http.ProblemDetail.forStatusAndDetail(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later.");
                problemDetail.setTitle("Too Many Requests");
                problemDetail.setProperty("retryAfter", retryAfter);
                
                httpResponse.getWriter().write(objectMapper.writeValueAsString(problemDetail));
                return;
            }

            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isRateLimitedPath(String path) {
        return path.equals("/api/auth/login") ||
               path.equals("/api/auth/refresh") ||
               path.equals("/api/auth/reset-password/request");
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
