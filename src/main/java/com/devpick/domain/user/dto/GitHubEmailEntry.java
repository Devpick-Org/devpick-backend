package com.devpick.domain.user.dto;

/**
 * GitHub /user/emails API 응답 엔트리.
 * 이메일 비공개 설정 사용자의 primary + verified 이메일 조회에 사용된다.
 */
public record GitHubEmailEntry(
        String email,
        boolean verified,
        boolean primary,
        String visibility
) {}
