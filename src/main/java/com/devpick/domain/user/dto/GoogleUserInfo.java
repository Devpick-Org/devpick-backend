package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleUserInfo(
        String id,
        String email,
        String name,
        @JsonProperty("picture") String pictureUrl
) {}
