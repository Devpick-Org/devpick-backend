package com.devpick.domain.content.collector.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stack Overflow API v2.3 answers 엔드포인트 응답 항목.
 * body_markdown 필드를 포함하려면 filter=withbody 파라미터가 필요하다.
 */
public record StackOverflowAnswer(
        @JsonProperty("answer_id") long answerId,
        @JsonProperty("body_markdown") String bodyMarkdown,
        @JsonProperty("score") int score,
        @JsonProperty("is_accepted") boolean isAccepted
) {
}