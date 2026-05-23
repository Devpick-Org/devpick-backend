package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoBookDocument(
        String title,
        List<String> authors,
        String publisher,
        String thumbnail,
        String url,
        String contents,
        String isbn,
        int price,
        @JsonProperty("sale_price") int salePrice,
        String datetime
) {
}
