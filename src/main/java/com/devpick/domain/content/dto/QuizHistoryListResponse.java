package com.devpick.domain.content.dto;

import java.util.List;

public record QuizHistoryListResponse(
        List<QuizHistoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
