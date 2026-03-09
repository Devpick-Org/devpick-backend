package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub OAuth 토큰 교환 응답 DTO.
 * 성공 시 accessToken, 실패 시 error 필드가 채워진다.
 * GitHub는 토큰 교환 실패에도 HTTP 200을 반환하고 body에 error를 담기 때문에
 * error 필드를 함께 매핑해야 한다.
 */
public record GitHubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {}
