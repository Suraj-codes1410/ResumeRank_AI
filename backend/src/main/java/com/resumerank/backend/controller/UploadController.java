package com.resumerank.backend.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.resumerank.backend.dto.UploadSignatureResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final Cloudinary cloudinary;

    public UploadController(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    @PostMapping("/signature")
    public ResponseEntity<UploadSignatureResponse> getUploadSignature(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        String folder = "resumes/" + userId.toString();

        Map<String, Object> paramsToSign = ObjectUtils.asMap(
                "folder", folder,
                "timestamp", timestamp,
                "allowed_formats", "pdf,docx"
        );

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        UploadSignatureResponse response = new UploadSignatureResponse(
                signature,
                timestamp,
                apiKey,
                cloudName,
                folder
        );

        return ResponseEntity.ok(response);
    }
}
