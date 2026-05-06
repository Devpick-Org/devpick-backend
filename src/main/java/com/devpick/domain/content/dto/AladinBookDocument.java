package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AladinBookDocument(
        String title,
        String author,
        String publisher,
        String cover,
        String link,
        String description,
        String isbn13,
        int priceSales,
        int priceStandard,
        int salesPoint
) {
}
