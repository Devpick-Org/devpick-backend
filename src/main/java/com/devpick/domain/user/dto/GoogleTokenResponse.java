package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OAuth 토큰 교환 응답 DTO.
 * Google은 HTTP 4xx로 에러를 내려주므로 WebClientResponseException body 파싱에 활용된다.
 * 에러 응답 body도 동일 구조이므로 error, error_description 필드를 추가한다.
 */
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {}
