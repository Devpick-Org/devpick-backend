package com.devpick.global.storage;

import com.devpick.domain.community.dto.PostAttachmentDTO;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_PROFILE_BYTES = 5 * 1024 * 1024L;
    private static final long MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024L;

    private static final Set<String> PROFILE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.public-base-url:}")
    private String publicBaseUrl;

    @Value("${aws.region:ap-northeast-2}")
    private String region;

    /**
     * 업로드된 객체 URL에 사용하는 퍼블릭 URL 접두사 (첨부 URL 검증에 사용).
     */
    public String getPublicUrlPrefix() {
        if (!StringUtils.hasText(bucket)) {
            return null;
        }
        if (StringUtils.hasText(publicBaseUrl)) {
            String u = publicBaseUrl.trim();
            return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    public String uploadProfileImage(UUID userId, MultipartFile file) {
        ensureBucketConfigured();
        validateSize(file, MAX_PROFILE_BYTES);
        validateProfileContentType(file);
        String ext = resolveExtension(file);
        String key = "profiles/" + userId + "/" + UUID.randomUUID() + ext;
        putObject(key, file, file.getContentType(), "inline");
        return buildPublicUrl(key);
    }

    public PostAttachmentDTO uploadPostAttachment(UUID userId, MultipartFile file) {
        ensureBucketConfigured();
        validateSize(file, MAX_ATTACHMENT_BYTES);
        validateAttachmentContentType(file);
        String contentType = file.getContentType();
        String type = classifyAttachmentType(contentType);
        String ext = resolveExtension(file);
        String key = "attachments/" + userId + "/" + UUID.randomUUID() + ext;
        String ct = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        putObject(key, file, ct, contentDispositionForPostAttachment(ct, file.getOriginalFilename()));
        String url = buildPublicUrl(key);
        return new PostAttachmentDTO(type, url, file.getOriginalFilename());
    }

    private void ensureBucketConfigured() {
        if (!StringUtils.hasText(bucket)) {
            throw new DevpickException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
        }
    }

    private void validateSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new DevpickException(ErrorCode.FILE_UPLOAD_TOO_LARGE);
        }
    }

    private void validateProfileContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null || !PROFILE_CONTENT_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            throw new DevpickException(ErrorCode.FILE_UPLOAD_INVALID_TYPE);
        }
    }

    private void validateAttachmentContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null) {
            throw new DevpickException(ErrorCode.FILE_UPLOAD_INVALID_TYPE);
        }
        String c = ct.toLowerCase(Locale.ROOT);
        if (c.startsWith("image/") || "application/pdf".equals(c)) {
            return;
        }
        throw new DevpickException(ErrorCode.FILE_UPLOAD_INVALID_TYPE);
    }

    /** 이미지는 inline, 그 외(PDF 등)는 attachment로 저장해 클릭 시 다운로드 유도 */
    private static String contentDispositionForPostAttachment(String contentType, String originalFilename) {
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) {
            return "inline";
        }
        String safe = sanitizeFilenameForContentDisposition(originalFilename);
        return "attachment; filename=\"" + safe + "\"";
    }

    private static String sanitizeFilenameForContentDisposition(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "file";
        }
        String base = originalFilename.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash < base.length() - 1) {
            base = base.substring(slash + 1);
        }
        return base.replace("\"", "'").replaceAll("[\\r\\n]", "_");
    }

    private void putObject(String key, MultipartFile file, String contentType, String contentDisposition) {
        try {
            PutObjectRequest.Builder b = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentDisposition(contentDisposition);
            PutObjectRequest req = b.build();
            try (InputStream in = file.getInputStream()) {
                s3Client.putObject(req, RequestBody.fromInputStream(in, file.getSize()));
            }
        } catch (IOException e) {
            throw new DevpickException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
        }
    }

    private String buildPublicUrl(String key) {
        return getPublicUrlPrefix() + "/" + key;
    }

    private static String classifyAttachmentType(String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return "image";
        }
        return "file";
    }

    private static String resolveExtension(MultipartFile file) {
        String orig = file.getOriginalFilename();
        if (orig != null && orig.contains(".")) {
            return orig.substring(orig.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        String ct = file.getContentType();
        if (ct == null) {
            return ".bin";
        }
        return switch (ct.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }

    /** URL이 이 저장소에서 발급된 퍼블릭 URL인지 검사 (게시글 첨부 등록 시). */
    public void validateUploadedAttachmentUrl(String url) {
        String prefix = getPublicUrlPrefix();
        if (prefix == null) {
            throw new DevpickException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
        }
        if (url == null || !url.startsWith(prefix)) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
    }

    public static String inferAttachmentTypeFromUrl(String url) {
        if (url == null) {
            return "file";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return "image";
        }
        return "file";
    }

    public static String extractFileNameFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash >= path.length() - 1) {
            return null;
        }
        try {
            return URLDecoder.decode(path.substring(slash + 1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return path.substring(slash + 1);
        }
    }
}
