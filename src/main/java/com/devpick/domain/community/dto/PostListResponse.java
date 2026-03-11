package com.devpick.domain.community.dto;

import java.util.List;

public record PostListResponse(
        List<PostSummaryResponse> posts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
