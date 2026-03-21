package com.devpick.domain.point.dto;

import java.util.List;

public record PointHistoryResponse(
        List<PointHistoryItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}