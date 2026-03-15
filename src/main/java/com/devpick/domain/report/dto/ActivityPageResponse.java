package com.devpick.domain.report.dto;

import java.util.List;

// DP-249: 내 활동 내역 페이지네이션 응답
public record ActivityPageResponse(
        List<ActivityItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
