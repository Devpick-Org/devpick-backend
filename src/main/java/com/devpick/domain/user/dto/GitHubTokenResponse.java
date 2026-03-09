package com.devpick.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub OAuth 토큰 교환 응답 DTO.
 * GitHub는 HTTP 200 OK 이지만 body에 error 필드를 담아 실패를 알리는 특이한 스펙이다.
 * error 필드를 추가하여 클라이언트 레이어에서 세분화된 에러 처리를 가능하게 한다.
 */
public record GitHubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {}
