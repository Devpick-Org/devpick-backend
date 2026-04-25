package com.devpick.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RelatedContentItem(
        @JsonProperty("content_id") String contentId,
        @JsonProperty("one_line_summary") String oneLineSummary
) {}
