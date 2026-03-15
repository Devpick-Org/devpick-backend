package com.devpick.domain.report.dto;

import java.util.List;

// DP-248: 학습 히스토리 페이지네이션 응답
public record HistoryPageResponse(
        List<HistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
