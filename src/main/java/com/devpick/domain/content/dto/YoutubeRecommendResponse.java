package com.devpick.domain.content.dto;

import java.util.List;

public record YoutubeRecommendResponse(
        List<YoutubeRecommendItem> videos,
        boolean isPersonalized,
        String message
) {
}
