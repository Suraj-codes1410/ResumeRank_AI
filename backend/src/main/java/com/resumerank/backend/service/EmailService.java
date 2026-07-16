package com.resumerank.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final String resendApiKey;
    private final String fromAddress;
    private final HttpClient httpClient;

    @Autowired
    public EmailService(
            @Value("${resend.api-key:}") String resendApiKey,
            @Value("${resend.from-address:noreply@resumerank.ai}") String fromAddress) {
        this.resendApiKey = resendApiKey;
        this.fromAddress = fromAddress;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send an email verification link to a newly registered user.
     * Failures are logged but never propagated — a transient email outage
     * should not block account creation.
     */
    public void sendVerificationEmail(String recipientEmail, String verificationLink) {
        String subject = "Verify your ResumeRank account";
        String html = buildEmailHtml(
                "Verify Your Email",
                "Click the button below to verify your email address and activate your ResumeRank account. This link expires in 24 hours.",
                "Verify Email",
                verificationLink
        );
        sendEmail(recipientEmail, subject, html);
    }

    /**
     * Send a password reset link to a user who requested it.
     * Failures are logged but never propagated.
     */
    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        String subject = "Reset your ResumeRank password";
        String html = buildEmailHtml(
                "Reset Your Password",
                "Click the button below to reset your password. This link expires in 20 minutes. If you did not request this, you can safely ignore this email.",
                "Reset Password",
                resetLink
        );
        sendEmail(recipientEmail, subject, html);
    }

    void sendEmail(String to, String subject, String html) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            logger.warn("RESEND_API_KEY is not configured — email to {} will not be sent. Subject: {}", to, subject);
            return;
        }

        try {
            String jsonBody = String.format(
                    """
                    {
                      "from": "%s",
                      "to": ["%s"],
                      "subject": "%s",
                      "html": %s
                    }
                    """,
                    escapeJson(fromAddress),
                    escapeJson(to),
                    escapeJson(subject),
                    toJsonString(html)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Email sent successfully to {} (subject: {})", to, subject);
            } else {
                logger.error("Resend API returned status {} for email to {}. Response: {}", response.statusCode(), to, response.body());
            }
        } catch (Exception e) {
            logger.error("Failed to send email to {} via Resend: {}", to, e.getMessage(), e);
        }
    }

    private String buildEmailHtml(String heading, String body, String ctaText, String ctaUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#10141A;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#10141A;padding:40px 20px;">
                    <tr><td align="center">
                      <table width="480" cellpadding="0" cellspacing="0" style="background-color:#1A1F28;border:1px solid #2A2F38;border-radius:8px;padding:40px;">
                        <tr><td style="padding-bottom:24px;border-bottom:1px solid #2A2F38;">
                          <span style="font-size:18px;font-weight:bold;color:#C99A52;letter-spacing:0.05em;">ResumeRank</span>
                        </td></tr>
                        <tr><td style="padding-top:32px;">
                          <h1 style="margin:0 0 16px;font-size:22px;color:#E8E6E1;">%s</h1>
                          <p style="margin:0 0 32px;font-size:14px;line-height:1.6;color:#9A9690;">%s</p>
                          <a href="%s" style="display:inline-block;padding:12px 32px;background-color:#C99A52;color:#10141A;font-size:14px;font-weight:600;text-decoration:none;border-radius:6px;">%s</a>
                          <p style="margin:32px 0 0;font-size:12px;line-height:1.5;color:#6A6560;">If the button doesn't work, copy and paste this link into your browser:<br><a href="%s" style="color:#C99A52;word-break:break-all;">%s</a></p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, body, ctaUrl, ctaText, ctaUrl, ctaUrl);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String toJsonString(String value) {
        return "\"" + escapeJson(value) + "\"";
    }
}
