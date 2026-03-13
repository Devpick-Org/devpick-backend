package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;

import java.util.UUID;

/**
 * 로그인/소셜 로그인 응답 DTO (DP-183, DP-184, DP-284).
 *
 * [이슈 1 결정 - DP-284] isNewUser 플래그 도입
 * - HTTP 200 vs 201로 신규/기존 유저를 구분하는 방식은 기각.
 *   상태코드를 UI 라우팅 신호로 오용하면 axios 인터셉터 설계가 오염되고
 *   RESTful 의미론(201 = 자원 생성)과 혼용되어 혼란을 초래함.
 * - 응답 바디의 isNewUser: boolean 플래그로 프론트가 온보딩/메인 화면을 분기.
 *   단일 성공 분기(200) 유지, 관심사 명확 분리.
 *
 * [보안 결정 - DP-181] refreshToken → HttpOnly Cookie 전환
 * - refreshToken은 JS로 접근 불가한 HttpOnly Cookie로 내려준다.
 * - XSS 공격으로 인한 refreshToken 탈취 방지.
 * - 응답 바디에서 refreshToken 필드 제거, accessToken만 바디로 반환.
 */
public record LoginResponse(
        String accessToken,
        UUID userId,
        String email,
        String nickname,
        boolean isNewUser
) {
    /** 기존 유저 로그인 — isNewUser = false */
    public static LoginResponse of(String accessToken, User user) {
        return new LoginResponse(accessToken, user.getId(), user.getEmail(), user.getNickname(), false);
    }

    /** 신규 유저 최초 가입 — isNewUser = true */
    public static LoginResponse ofNewUser(String accessToken, User user) {
        return new LoginResponse(accessToken, user.getId(), user.getEmail(), user.getNickname(), true);
    }
}
