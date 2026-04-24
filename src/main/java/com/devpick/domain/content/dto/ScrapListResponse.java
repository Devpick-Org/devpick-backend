package com.devpick.domain.content.dto;

import java.util.List;

public record ScrapListResponse(
        List<ScrapItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}