package com.devpick.domain.user.dto;

/**
 * 토큰 재발급 응답 DTO (DP-181).
 *
 * [보안 결정 - DP-181] refreshToken → HttpOnly Cookie 전환
 * - refreshToken은 Set-Cookie 헤더로 내려주므로 바디에서 제거.
 * - accessToken만 바디로 반환한다.
 */
public record TokenResponse(
        String accessToken
) {
}
