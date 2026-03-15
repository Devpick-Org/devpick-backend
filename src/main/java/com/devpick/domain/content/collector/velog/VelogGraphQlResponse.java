package com.devpick.domain.content.collector.velog;

import java.util.List;

/**
 * Velog GraphQL API 응답 래퍼.
 * { "data": { "trendingPosts": [ ... ] } }
 */
public record VelogGraphQlResponse(
        Data data
) {

    public record Data(List<VelogPost> trendingPosts) {}

    /**
     * null-safe 하게 게시물 목록을 반환한다.
     */
    public List<VelogPost> posts() {
        if (data == null || data.trendingPosts() == null) {
            return List.of();
        }
        return data.trendingPosts();
    }
}
