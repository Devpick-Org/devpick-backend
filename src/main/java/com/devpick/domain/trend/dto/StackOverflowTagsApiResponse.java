package com.devpick.domain.trend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Stack Overflow API v2.3 Tags 응답 래퍼.
 * GET /tags 엔드포인트 기준.
 */
public record StackOverflowTagsApiResponse(
        @JsonProperty("items") List<TagItem> items,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("quota_remaining") int quotaRemaining
) {
    public record TagItem(
            @JsonProperty("name") String name,
            @JsonProperty("count") int count
    ) {}
}
