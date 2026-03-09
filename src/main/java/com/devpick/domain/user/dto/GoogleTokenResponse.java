package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OAuth 토큰 교환 응답 DTO.
 * 성공 시 accessToken, 실패 시 HTTP 4xx + error/error_description 필드.
 */
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {}
