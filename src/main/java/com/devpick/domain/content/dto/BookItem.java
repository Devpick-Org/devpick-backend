package com.devpick.domain.content.dto;

import java.util.List;

public record BookItem(
        String title,
        List<String> authors,
        String publisher,
        String thumbnail,
        String url,
        String contents
) {
}
