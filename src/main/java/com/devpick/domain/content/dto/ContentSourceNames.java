package com.devpick.domain.content.dto;

/**
 * API 응답에서 소스별 노출 정책(원문·SO 전용 필드 비노출 등)에 쓰는 이름 판별.
 */
public final class ContentSourceNames {

    private ContentSourceNames() {
    }

    public static boolean isStackOverflow(String sourceName) {
        return sourceName != null && "stack overflow".equalsIgnoreCase(sourceName.trim());
    }
}
