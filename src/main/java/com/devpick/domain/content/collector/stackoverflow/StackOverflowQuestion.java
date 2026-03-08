package com.devpick.domain.content.collector.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Stack Overflow API v2.3 question 항목.
 * creation_date는 Unix timestamp (seconds).
 */
public record StackOverflowQuestion(
        @JsonProperty("question_id") long questionId,
        @JsonProperty("title") String title,
        @JsonProperty("link") String link,
        @JsonProperty("owner") Owner owner,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("body_markdown") String bodyMarkdown,
        @JsonProperty("creation_date") long creationDate,
        @JsonProperty("score") int score,
        @JsonProperty("view_count") int viewCount,
        @JsonProperty("is_answered") boolean isAnswered
) {

    public record Owner(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("account_id") Long accountId
    ) {
    }
}
