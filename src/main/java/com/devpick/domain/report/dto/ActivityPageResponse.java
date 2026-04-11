package com.devpick.domain.report.dto;

import java.util.List;

public record ActivityPageResponse(
        List<ActivityItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
