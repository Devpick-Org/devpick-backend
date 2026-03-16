package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * 일반(이메일) 로그인 응답 DTO (DP-180).
 * 소셜 로그인은 isNewUser 플래그가 필요하므로 SocialLoginResponse를 사용한다.
 *
 * [보안 결정 - DP-181] refreshToken → HttpOnly Cookie 전환
 * - refreshToken은 JS로 접근 불가한 HttpOnly Cookie로 내려준다.
 * - XSS 공격으로 인한 refreshToken 탈취 방지.
 * - refreshTokenValue 필드는 @JsonIgnore로 바디 직렬화에서 제외.
 *   컨트롤러에서 Cookie Set 후 프론트에는 노출되지 않는다.
 */
public record LoginResponse(
        String accessToken,
        UUID userId,
        String email,
        String nickname,
        @JsonIgnore String refreshTokenValue
) {
    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(accessToken, user.getId(), user.getEmail(), user.getNickname(), refreshToken);
    }
}
