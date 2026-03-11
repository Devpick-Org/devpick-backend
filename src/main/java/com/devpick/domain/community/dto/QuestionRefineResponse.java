package com.devpick.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QuestionRefineResponse(
        @JsonProperty("refined_title") String refinedTitle,
        @JsonProperty("refined_content") String refinedContent,
        @JsonProperty("suggestions") List<String> suggestions
) {}
