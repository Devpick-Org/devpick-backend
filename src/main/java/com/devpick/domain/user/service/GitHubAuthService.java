package com.devpick.domain.user.service;

import com.devpick.domain.user.client.GitHubOAuthClient;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GitHubAuthService {

    private static final String GITHUB_PROVIDER = "github";

    private final GitHubOAuthClient gitHubOAuthClient;
    private final OAuthStateService oAuthStateService;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * GitHub OAuth 시작 URL 발급 (DP-284).
     * state UUID를 Redis에 저장(5분)하고 인가 URL에 포함.
     */
    public OAuthAuthorizationResponse generateAuthorizationUrl() {
        String state = oAuthStateService.generateState();
        return new OAuthAuthorizationResponse(gitHubOAuthClient.getAuthorizationUrl(state));
    }

    /**
     * GitHub 소셜 로그인 처리 (DP-183, DP-284).
     * 1. state 검증 (CSRF 방지) → Redis에서 확인 후 삭제
     * 2. 인가 코드 → GitHub Access Token 교환
     * 3. GitHub 사용자 정보 조회
     * 4. 기존 소셜 계정 조회 or 신규 User + SocialAccount 생성
     * 5. JWT Access + Refresh Token 발급
     */
    @Transactional
    public LoginResponse login(String code, String state) {
        oAuthStateService.validateAndDeleteState(state);
        String githubAccessToken = gitHubOAuthClient.exchangeToken(code);
        GitHubUserInfo userInfo = gitHubOAuthClient.fetchUserInfo(githubAccessToken);

        User user = socialAccountRepository
                .findByProviderAndProviderId(GITHUB_PROVIDER, userInfo.id())
                .map(SocialAccount::getUser)
                .orElseGet(() -> registerNewSocialUser(userInfo));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiresAt())
                .build());

        return LoginResponse.of(accessToken, refreshToken, user);
    }

    private User registerNewSocialUser(GitHubUserInfo userInfo) {
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
        }

        String nickname = resolveNickname(userInfo);
        User newUser = User.createSocialUser(userInfo.email(), nickname);
        userRepository.save(newUser);

        SocialAccount socialAccount = SocialAccount.builder()
                .user(newUser)
                .provider(GITHUB_PROVIDER)
                .providerId(userInfo.id())
                .build();
        socialAccountRepository.save(socialAccount);

        return newUser;
    }

    /**
     * 닉네임 후보: name → login 순으로 사용. 중복 시 login + 숫자 suffix 추가.
     * 확장 포인트 (DP-183): 닉네임 중복 처리 정책을 별도 NicknameGenerator로 분리 가능.
     */
    private String resolveNickname(GitHubUserInfo userInfo) {
        String candidate = (userInfo.name() != null && !userInfo.name().isBlank())
                ? userInfo.name()
                : userInfo.login();

        if (!userRepository.existsByNickname(candidate)) {
            return candidate;
        }
        return userInfo.login() + "_" + userInfo.id();
    }
}
