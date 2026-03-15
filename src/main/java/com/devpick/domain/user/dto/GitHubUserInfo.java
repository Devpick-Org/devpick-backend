package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub API /user 응답 DTO.
 * OAuthUserInfo를 구현하여 Strategy Pattern에 참여한다.
 */
public record GitHubUserInfo(
        String id,
        String login,
        String email,
        String name,
        @JsonProperty("avatar_url") String avatarUrl
) implements OAuthUserInfo {

    @Override
    public String getProviderId() {
        return id;
    }

    @Override
    public String getEmail() {
        return email;
    }

    /**
     * GitHub 닉네임 prefix: login (username).
     * name은 선택적이므로 Service 레이어의 NicknameGenerator가 name → prefix 순으로 시도한다.
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNicknamePrefix() {
        return login;
    }
}
