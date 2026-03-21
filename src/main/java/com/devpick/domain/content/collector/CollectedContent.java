package com.devpick.domain.content.collector;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 각 수집기(Collector)가 반환하는 공통 수집 결과 포맷.
 * SO 전용 필드(score, viewCount, isAnswered, questionContent, acceptedAnswer, topAnswers)는
 * Stack Overflow 외 소스에서는 null이다. {@link #of} 팩토리 메서드를 사용하면 null로 자동 처리된다.
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
        List<String> tags,
        // Stack Overflow 전용 필드 (비-SO 소스는 null)
        Integer score,
        Integer viewCount,
        Boolean isAnswered,
        String questionContent,
        StackOverflowAnswerDto acceptedAnswer,
        List<StackOverflowAnswerDto> topAnswers
) {
    /**
     * Stack Overflow 외 수집기(Velog 등)용 팩토리 메서드.
     * SO 전용 필드는 null로 설정된다.
     */
    public static CollectedContent of(
            String title, String author, String canonicalUrl,
            String preview, String originalContent, boolean isOriginalVisible,
            String licenseType, LocalDateTime publishedAt, List<String> tags) {
        return new CollectedContent(title, author, canonicalUrl, preview, originalContent,
                isOriginalVisible, licenseType, publishedAt, tags,
                null, null, null, null, null, null);
    }
}
