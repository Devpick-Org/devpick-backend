package com.devpick.domain.user.service;

import com.devpick.domain.user.client.GoogleOAuthClient;
import com.devpick.domain.user.dto.GoogleUserInfo;
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
public class GoogleAuthService {

    private static final String GOOGLE_PROVIDER = "google";

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthStateService oAuthStateService;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Google OAuth 시작 URL 발급 (DP-284).
     * state UUID를 Redis에 저장(5분)하고 인가 URL에 포함.
     */
    public OAuthAuthorizationResponse generateAuthorizationUrl() {
        String state = oAuthStateService.generateState();
        return new OAuthAuthorizationResponse(googleOAuthClient.getAuthorizationUrl(state));
    }

    /**
     * Google 소셜 로그인 처리 (DP-184, DP-284).
     * 1. state 검증 (CSRF 방지) → Redis에서 확인 후 삭제
     * 2. 인가 코드 → Google Access Token 교환
     * 3. Google 사용자 정보 조회
     * 4. 기존 소셜 계정 조회 or 신규 User + SocialAccount 생성
     * 5. JWT Access + Refresh Token 발급
     */
    @Transactional
    public LoginResponse login(String code, String state) {
        oAuthStateService.validateAndDeleteState(state);
        String googleAccessToken = googleOAuthClient.exchangeToken(code);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(googleAccessToken);

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED);
        }

        User user = socialAccountRepository
                .findByProviderAndProviderId(GOOGLE_PROVIDER, userInfo.id())
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

    private User registerNewSocialUser(GoogleUserInfo userInfo) {
        String nickname = resolveNickname(userInfo);
        User newUser = User.createSocialUser(userInfo.email(), nickname);
        userRepository.save(newUser);

        SocialAccount socialAccount = SocialAccount.builder()
                .user(newUser)
                .provider(GOOGLE_PROVIDER)
                .providerId(userInfo.id())
                .build();
        socialAccountRepository.save(socialAccount);

        return newUser;
    }

    /**
     * 닉네임 후보: name → email 앞부분 순으로 사용. 중복 시 email 앞부분 + id suffix 추가.
     * 확장 포인트 (DP-184): 닉네임 중복 처리 정책을 별도 NicknameGenerator로 분리 가능.
     */
    private String resolveNickname(GoogleUserInfo userInfo) {
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
