package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AladinBookResponse(
        List<AladinBookDocument> item
) {
}
