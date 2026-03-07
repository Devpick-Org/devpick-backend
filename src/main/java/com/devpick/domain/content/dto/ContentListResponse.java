package com.devpick.domain.content.dto;

import java.util.List;

public record ContentListResponse(
        List<ContentSummaryResponse> contents,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
