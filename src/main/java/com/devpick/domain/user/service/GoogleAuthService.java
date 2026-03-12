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

import java.util.Optional;

/**
 * Google 소셜 로그인 서비스 (DP-184, DP-284).
 *
 * [이슈 1 결정 - DP-284] isNewUser 플래그
 * - 신규 가입 여부를 login() 반환값에 포함. SocialAccount 존재 여부로 판별.
 * - 기존 유저: LoginResponse.of(), 신규 유저: LoginResponse.ofNewUser()
 *
 * [이슈 2 결정 - DP-184] 닉네임 중복 처리 → emailPrefix + id suffix 확정
 * - 2-step 가입(tempToken) 방식은 현재 Confluence 스펙에 정의 없어 기각.
 * - NicknameGenerator에 위임하여 정책 변경 시 단일 지점에서 수정 가능.
 *
 * [이슈 3 확인 - DP-284] 환경별 Redirect URI
 * - oauth.frontend-base-url이 application-local.yml / application-docker.yml에
 *   이미 분리되어 있어 추가 작업 불필요.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private static final String GOOGLE_PROVIDER = "google";

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthStateService oAuthStateService;
    private final NicknameGenerator nicknameGenerator;
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
     * 6. isNewUser 플래그를 응답에 포함 (DP-284 이슈 1)
     */
    @Transactional
    public LoginResponse login(String code, String state) {
        oAuthStateService.validateAndDeleteState(state);
        String googleAccessToken = googleOAuthClient.exchangeToken(code);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(googleAccessToken);

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED);
        }

        Optional<SocialAccount> existingAccount =
                socialAccountRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, userInfo.id());

        boolean isNewUser = existingAccount.isEmpty();
        User user = existingAccount
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

        return isNewUser
                ? LoginResponse.ofNewUser(accessToken, refreshToken, user)
                : LoginResponse.of(accessToken, refreshToken, user);
    }

    private User registerNewSocialUser(GoogleUserInfo userInfo) {
        // [이슈 2] NicknameGenerator에 중복 처리 위임 (DP-184)
        String nickname = nicknameGenerator.generateFromGoogle(userInfo);
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
}
