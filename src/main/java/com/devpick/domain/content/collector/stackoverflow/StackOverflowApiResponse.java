package com.devpick.domain.content.collector.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Stack Overflow API v2.3 응답 래퍼.
 * GET /questions 엔드포인트 기준.
 */
public record StackOverflowApiResponse(
        @JsonProperty("items") List<StackOverflowQuestion> items,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("quota_remaining") int quotaRemaining
) {
}
