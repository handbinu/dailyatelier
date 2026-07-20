package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.CloudinarySignatureResponseDto;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private static final String ALLOWED_FOLDER = "arts";

    private final UserRepository userRepository;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @PostConstruct
    void validateConfiguration() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("Cloudinary environment variables are not configured");
        }
    }

    public CloudinarySignatureResponseDto createUploadSignature(String userId, String folder) {
        validateArtist(userId);
        String normalizedFolder = normalizeFolder(folder);
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = generateSignature(normalizedFolder, timestamp);

        return new CloudinarySignatureResponseDto(
                cloudName,
                apiKey,
                normalizedFolder,
                timestamp,
                signature,
                buildUploadUrl()
        );
    }

    private void validateArtist(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only artist users can upload artworks");
        }
    }

    private String normalizeFolder(String folder) {
        String value = folder == null ? "" : folder.trim();
        if (!ALLOWED_FOLDER.equals(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only arts folder is allowed");
        }
        return value;
    }

    private String generateSignature(String folder, long timestamp) {
        String payload = "folder=" + folder + "&timestamp=" + timestamp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate Cloudinary signature", e);
        }
    }

    private String buildUploadUrl() {
        return "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
