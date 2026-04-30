package com.devpick.domain.content.dto;

import java.util.List;

public record BookRecommendResponse(
        List<BookItem> books,
        boolean isPersonalized,
        String message
) {
}
