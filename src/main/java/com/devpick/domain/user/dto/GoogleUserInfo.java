package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google API /userinfo 응답 DTO.
 * OAuthUserInfo를 구현하여 Strategy Pattern에 참여한다.
 */
public record GoogleUserInfo(
        String id,
        String email,
        String name,
        @JsonProperty("picture") String pictureUrl
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
     * Google 닉네임 prefix: email @ 앞부분.
     * name은 선택적이므로 Service 레이어의 NicknameGenerator가 name → prefix 순으로 시도한다.
     */
    @Override
    public String getNicknamePrefix() {
        int atIdx = (email != null) ? email.indexOf('@') : -1;
        return atIdx > 0 ? email.substring(0, atIdx) : email;
    }
}
