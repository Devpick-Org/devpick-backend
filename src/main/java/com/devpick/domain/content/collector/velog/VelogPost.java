package com.devpick.domain.content.collector.velog;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Velog GraphQL trendingPosts 응답에서 파싱되는 게시물 객체.
 */
public record VelogPost(
        String id,
        String title,

        @JsonProperty("short_description")
        String shortDescription,

        @JsonProperty("url_slug")
        String urlSlug,

        @JsonProperty("released_at")
        String releasedAt,

        List<String> tags,

        VelogUser user
) {

    public record VelogUser(String username) {}
}
