package com.devpick.domain.user.dto;

/**
 * 소셜 로그인 제공자별 사용자 정보를 추상화한 공통 인터페이스.
 * Strategy Pattern의 ConcreteStrategy(GitHubUserInfo, GoogleUserInfo)가 구현한다.
 */
public interface OAuthUserInfo {

    /** 제공자 고유 사용자 ID (providerId로 사용) */
    String getProviderId();

    /** 사용자 이메일 */
    String getEmail();

    /**
     * 사용자 표시 이름 (선택 값). null이면 NicknameGenerator가 getNicknamePrefix()로 폴백.
     * - GitHub: name (display name)
     * - Google: name (full name)
     */
    default String getName() { return null; }

    /**
     * 닉네임 생성의 1차 후보 prefix.
     * - GitHub: login
     * - Google: email @ 앞부분
     */
    String getNicknamePrefix();
}
