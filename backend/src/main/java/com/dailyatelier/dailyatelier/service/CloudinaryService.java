package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.CloudinarySignatureResponseDto;
import com.dailyatelier.dailyatelier.dto.CloudinaryUploadResult;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.annotation.PostConstruct;
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
import java.net.URI;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryService {
    private static final String ALLOWED_FOLDER = "arts";
    private static final long MAX_INQUIRY_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_INQUIRY_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    );
    private static final Set<String> ALLOWED_PROFILE_IMAGE_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE
    );

    private final UserRepository userRepository;
    private final RestClient.Builder restClientBuilder;

    public CloudinaryService(
            UserRepository userRepository,
            RestClient.Builder restClientBuilder) {
        this.userRepository = userRepository;
        this.restClientBuilder = restClientBuilder;
    }

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
        String normalizedFolder = normalizeArtFolder(userId, folder);
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
            Map<?, ?> response = restClientBuilder.build()
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

    public CloudinaryUploadResult uploadProfileImage(String userId, MultipartFile image) {
        validateProfileImage(image);
        String folder = "profiles/" + userId;
        long timestamp = System.currentTimeMillis() / 1000L;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", image.getResource());
        body.add("folder", folder);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("api_key", apiKey);
        body.add("signature", generateSignature(folder, timestamp));

        try {
            Map<?, ?> response = restClientBuilder.build()
                    .post()
                    .uri(buildUploadUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String secureUrl = response == null ? null : (String) response.get("secure_url");
            String publicId = response == null ? null : (String) response.get("public_id");
            if (isBlank(secureUrl)
                    || isBlank(publicId)
                    || !publicId.startsWith(folder + "/")) {
                throw new DomainApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PROFILE_IMAGE_UPLOAD_FAILED",
                        "프로필 이미지 업로드에 실패했습니다."
                );
            }
            return new CloudinaryUploadResult(secureUrl, publicId);
        } catch (RestClientException exception) {
            throw new DomainApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PROFILE_IMAGE_UPLOAD_FAILED",
                    "프로필 이미지 업로드에 실패했습니다.",
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

    public void validateArtImageReference(
            String userId,
            String secureUrl,
            String publicId) {
        String namespace = "arts/" + userId + "/";
        if (isBlank(secureUrl)
                || isBlank(publicId)
                || !publicId.startsWith(namespace)) {
            throw invalidArtImageReference();
        }

        try {
            URI uri = URI.create(secureUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw invalidArtImageReference();
            }

            String prefix = "/" + cloudName + "/image/upload/";
            String path = uri.getPath();
            if (path == null || !path.startsWith(prefix)) {
                throw invalidArtImageReference();
            }

            String versionAndAsset = path.substring(prefix.length());
            int versionSeparator = versionAndAsset.indexOf('/');
            if (versionSeparator < 0
                    || !versionAndAsset.substring(0, versionSeparator).matches("v[0-9]+")) {
                throw invalidArtImageReference();
            }

            String assetWithExtension = versionAndAsset.substring(versionSeparator + 1);
            int lastSlash = assetWithExtension.lastIndexOf('/');
            int extensionSeparator = assetWithExtension.lastIndexOf('.');
            if (extensionSeparator <= lastSlash
                    || extensionSeparator == assetWithExtension.length() - 1) {
                throw invalidArtImageReference();
            }
            String urlPublicId = assetWithExtension.substring(0, extensionSeparator);
            if (!publicId.equals(urlPublicId)) {
                throw invalidArtImageReference();
            }
        } catch (IllegalArgumentException exception) {
            throw invalidArtImageReference();
        }
    }

    private DomainApiException invalidArtImageReference() {
        return new DomainApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ART_IMAGE_REFERENCE",
                "작품 이미지 정보를 확인해 주세요."
        );
    }

    private String normalizeArtFolder(String userId, String folder) {
        String value = folder == null ? "" : folder.trim();
        if (!ALLOWED_FOLDER.equals(value)) {
            throw new DomainApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_FOLDER", "허용되지 않은 업로드 폴더입니다.");
        }
        return value + "/" + userId;
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

    private void validateProfileImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROFILE_IMAGE",
                    "프로필 이미지 파일을 확인해 주세요."
            );
        }
        if (image.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_IMAGE_TOO_LARGE",
                    "프로필 이미지는 5MB 이하여야 합니다."
            );
        }
        if (!ALLOWED_PROFILE_IMAGE_CONTENT_TYPES.contains(image.getContentType())) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROFILE_IMAGE_TYPE",
                    "JPG, PNG 이미지만 업로드할 수 있습니다."
            );
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
