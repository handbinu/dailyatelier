package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.CloudinarySignatureResponseDto;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private static final String ALLOWED_FOLDER = "arts";
    private static final long MAX_INQUIRY_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_INQUIRY_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    );

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

    public InquiryAttachment uploadInquiryAttachment(String userId, MultipartFile attachment) {
        validateInquiryAttachment(attachment);
        String folder = "inquiries/" + userId;
        long timestamp = System.currentTimeMillis() / 1000L;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", attachment.getResource());
        body.add("folder", folder);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("api_key", apiKey);
        body.add("signature", generateSignature(folder, timestamp));

        try {
            Map<?, ?> response = RestClient.create()
                    .post()
                    .uri(buildUploadUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String secureUrl = response == null ? null : (String) response.get("secure_url");
            String resourceType = response == null ? null : (String) response.get("resource_type");
            if (isBlank(secureUrl) || isBlank(resourceType)) {
                throw new DomainApiException(
                        HttpStatus.BAD_GATEWAY,
                        "INQUIRY_ATTACHMENT_UPLOAD_FAILED",
                        "문의 첨부 파일 업로드에 실패했습니다."
                );
            }
            return new InquiryAttachment(secureUrl, attachment.getOriginalFilename(), resourceType);
        } catch (RestClientException exception) {
            throw new DomainApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INQUIRY_ATTACHMENT_UPLOAD_FAILED",
                    "문의 첨부 파일 업로드에 실패했습니다.",
                    exception
            );
        }
    }

    private void validateArtist(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new DomainApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new DomainApiException(HttpStatus.FORBIDDEN, "ARTIST_UPLOAD_FORBIDDEN", "작가 회원만 작품 이미지를 업로드할 수 있습니다.");
        }
    }

    private String normalizeFolder(String folder) {
        String value = folder == null ? "" : folder.trim();
        if (!ALLOWED_FOLDER.equals(value)) {
            throw new DomainApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_FOLDER", "허용되지 않은 업로드 폴더입니다.");
        }
        return value;
    }

    private void validateInquiryAttachment(MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            throw new DomainApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT", "첨부 파일을 확인해 주세요.");
        }
        if (attachment.getSize() > MAX_INQUIRY_ATTACHMENT_SIZE) {
            throw new DomainApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE", "첨부 파일은 10MB 이하여야 합니다.");
        }
        if (!ALLOWED_INQUIRY_CONTENT_TYPES.contains(attachment.getContentType())) {
            throw new DomainApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_TYPE", "JPG, PNG, PDF 파일만 첨부할 수 있습니다.");
        }
    }

    private String generateSignature(String folder, long timestamp) {
        String payload = "folder=" + folder + "&timestamp=" + timestamp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new DomainApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CLOUDINARY_SIGNATURE_FAILED",
                    "Cloudinary 업로드 서명 생성에 실패했습니다.",
                    e
            );
        }
    }

    private String buildUploadUrl() {
        return "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record InquiryAttachment(String url, String originalFilename, String resourceType) {
    }
}
