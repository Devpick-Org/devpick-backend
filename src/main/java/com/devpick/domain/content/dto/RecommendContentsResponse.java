package com.devpick.domain.content.dto;

import java.util.List;

public record RecommendContentsResponse(
        List<ContentSummaryResponse> contents,
        boolean isPersonalized,
        String message
) {
}
