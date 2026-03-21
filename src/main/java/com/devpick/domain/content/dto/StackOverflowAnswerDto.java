package com.devpick.domain.content.dto;

/**
 * Stack Overflow 답변 저장/응답 DTO.
 * acceptedAnswer(채택 답변)와 topAnswers(추천수 상위 답변) 양쪽에 사용된다.
 * DB에는 JSONB(TEXT) 형태로 직렬화되어 저장된다.
 */
public record StackOverflowAnswerDto(
        String body,
        int score
) {
}
