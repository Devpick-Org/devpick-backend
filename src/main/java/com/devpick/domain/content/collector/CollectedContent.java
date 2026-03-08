package com.devpick.domain.content.collector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 각 수집기(Collector)가 반환하는 공통 수집 결과 포맷.
 * 확장 포인트 (DP-200): 소스별 Collector가 이 포맷으로 변환해서 반환한다.
 */
public record CollectedContent(
        String title,
        String author,
        String canonicalUrl,
        String preview,
        String originalContent,
        boolean isOriginalVisible,
        String licenseType,
        LocalDateTime publishedAt,
        List<String> tags
) {
}
