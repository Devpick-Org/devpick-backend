package com.devpick.domain.trend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 부트캠퍼 {@code _next/image} 리소스는 외부 사이트 Referer 차단 등으로 브라우저 직접 로드가 실패하는 경우가 있어,
 * 서버가 부트캠퍼와 동일한 요청 패턴으로 이미지를 받아 브라우저에 넘깁니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BootcampThumbnailProxyController {

    private static final String BOOTCAMP_ORIGIN = "https://bootcamper.co.kr";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/131.0.0.0 Safari/537.36";

    /** {@code ../} 등 차단 (/uploads/파일명) */
    private static final Pattern ALLOWED_UPLOAD_PATH = Pattern.compile("^/uploads/[A-Za-z0-9_.\\-]+$");

    private final WebClient webClient;

    @GetMapping({"/trends/ecosystem/bootcamp-thumbnail", "/v1/trends/ecosystem/bootcamp-thumbnail"})
    public ResponseEntity<byte[]> proxyThumbnail(@RequestParam("path") String path) {
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String decoded = URLDecoder.decode(path.strip(), StandardCharsets.UTF_8);
            if (!ALLOWED_UPLOAD_PATH.matcher(decoded).matches()) {
                return ResponseEntity.badRequest().build();
            }
            String enc = URLEncoder.encode(decoded, StandardCharsets.UTF_8).replace("+", "%20");
            String upstream = BOOTCAMP_ORIGIN + "/_next/image?url=" + enc + "&w=640&q=75";

            byte[] body = webClient.get()
                    .uri(upstream)
                    .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en;q=0.8")
                    .header(HttpHeaders.REFERER, BOOTCAMP_ORIGIN + "/class")
                    .header(HttpHeaders.USER_AGENT, UA)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(25))
                    .block();

            if (body == null || body.length == 0) {
                return ResponseEntity.status(502).build();
            }
            MediaType mediaType =
                    sniffImageMediaType(decoded).orElse(MediaType.parseMediaType("image/jpeg"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600, stale-while-revalidate=86400")
                    .contentType(mediaType)
                    .body(body);
        } catch (WebClientResponseException e) {
            log.debug("부트캠퍼 썸네일 프록시 upstream {} {}", e.getStatusCode().value(), path);
            return ResponseEntity.status(e.getStatusCode().value()).build();
        } catch (Exception e) {
            log.warn("부트캠퍼 썸네일 프록시 실패: {} — {}", path, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private static java.util.Optional<MediaType> sniffImageMediaType(String uploadPath) {
        String p = uploadPath.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".png")) {
            return java.util.Optional.of(MediaType.parseMediaType("image/png"));
        }
        if (p.endsWith(".webp")) {
            return java.util.Optional.of(MediaType.parseMediaType("image/webp"));
        }
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) {
            return java.util.Optional.of(MediaType.parseMediaType("image/jpeg"));
        }
        return java.util.Optional.empty();
    }
}
