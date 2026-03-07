package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserInfo(
        String id,
        String login,
        String email,
        String name,
        @JsonProperty("avatar_url") String avatarUrl
) {}
