package com.devpick.domain.content.collector.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Stack Overflow API v2.3 answers 엔드포인트 응답 래퍼.
 */
public record StackOverflowAnswerApiResponse(
        @JsonProperty("items") List<StackOverflowAnswer> items,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("quota_remaining") int quotaRemaining
) {
}