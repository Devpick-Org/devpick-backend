package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.domain.user.dto.OAuthUserInfo;
import com.devpick.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OAuth 소셜 로그인 닉네임 생성 전략 (DP-183, DP-184).
 *
 * [이슈 2 결정] 닉네임 중복 시 suffix 방식 확정
 * - 2-step 가입(tempToken + 닉네임 입력 폼 유도) 방식은 기각.
 *   현재 Confluence 연동 규격에 해당 플로우 정의 없음.
 *   프론트 추가 구현 비용 대비 효과 불명확, 현행 즉시 완료 방식 유지.
 * - 중복 시 login(GitHub) 또는 emailPrefix(Google) + "_" + providerId suffix 부여.
 * - 향후 정책 변경 시 이 클래스만 수정하면 되도록 단일 책임으로 분리.
 */
@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private final UserRepository userRepository;

    /**
     * 소셜 로그인 공통 닉네임 생성 (SocialAuthService에서 사용).
     * 후보: getName() → getNicknamePrefix() 순. 중복 시 prefix + "_" + providerId.
     */
    public String generate(OAuthUserInfo userInfo) {
        String name = userInfo.getName();
        String candidate = (name != null && !name.isBlank()) ? name : userInfo.getNicknamePrefix();
        if (!userRepository.existsByNickname(candidate)) {
            return candidate;
        }
        return userInfo.getNicknamePrefix() + "_" + userInfo.getProviderId();
    }

    /**
     * GitHub 유저 닉네임 생성.
     * 후보: name → login 순. 중복 시 login + "_" + id.
     */
    public String generateFromGitHub(GitHubUserInfo userInfo) {
        String candidate = (userInfo.name() != null && !userInfo.name().isBlank())
                ? userInfo.name()
                : userInfo.login();

        if (!userRepository.existsByNickname(candidate)) {
            return candidate;
        }
        return userInfo.login() + "_" + userInfo.id();
    }

    /**
     * Google 유저 닉네임 생성.
     * 후보: name → email 앞부분 순. 중복 시 emailPrefix + "_" + id.
     */
    public String generateFromGoogle(GoogleUserInfo userInfo) {
        String candidate = (userInfo.name() != null && !userInfo.name().isBlank())
                ? userInfo.name()
                : extractEmailPrefix(userInfo.email());

        if (!userRepository.existsByNickname(candidate)) {
            return candidate;
        }
        return extractEmailPrefix(userInfo.email()) + "_" + userInfo.id();
    }

    private String extractEmailPrefix(String email) {
        int atIdx = email.indexOf('@');
        return atIdx > 0 ? email.substring(0, atIdx) : email;
    }
}
