package com.devpick.domain.user.service;

import com.devpick.domain.user.client.OAuthProviderClient;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.OAuthUserInfo;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소셜 로그인 통합 서비스 (Strategy Pattern).
 *
 * 콜 흐름:
 * 1. providerName으로 OAuthProviderClient를 라우팅
 * 2. 코드 → accessToken 교환
 * 3. accessToken → userInfo 조회
 * 4. SocialAccount 존재 조회 or 신규 회원 등록
 * 5. JWT 발급
 */
@Service
public class SocialAuthService {

    private final Map<String, OAuthProviderClient> clientMap;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final NicknameGenerator nicknameGenerator;

    public SocialAuthService(List<OAuthProviderClient> clients,
                             UserRepository userRepository,
                             SocialAccountRepository socialAccountRepository,
                             RefreshTokenRepository refreshTokenRepository,
                             JwtTokenProvider jwtTokenProvider,
                             NicknameGenerator nicknameGenerator) {
        this.clientMap = clients.stream()
                .collect(Collectors.toUnmodifiableMap(
                        OAuthProviderClient::getProviderName,
                        Function.identity()
                ));
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.nicknameGenerator = nicknameGenerator;
    }

    /**
     * 소셜 로그인 진입점.
     *
     * @param providerName "github" 또는 "google"
     * @param code         OAuth 콜백 코드
     */
    @Transactional
    public LoginResponse login(String providerName, String code) {
        OAuthProviderClient client = clientMap.get(providerName);
        if (client == null) {
            throw new DevpickException(ErrorCode.AUTH_OAUTH_UNSUPPORTED_PROVIDER);
        }

        String providerToken = client.exchangeToken(code);
        OAuthUserInfo userInfo = client.fetchUserInfo(providerToken);

        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
        }

        User user = socialAccountRepository
                .findByProviderAndProviderId(providerName, userInfo.getProviderId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> registerNewSocialUser(providerName, userInfo));

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

    private User registerNewSocialUser(String providerName, OAuthUserInfo userInfo) {
        String nickname = nicknameGenerator.generate(null, userInfo);
        User newUser = User.createSocialUser(userInfo.getEmail(), nickname);
        userRepository.save(newUser);

        SocialAccount socialAccount = SocialAccount.builder()
                .user(newUser)
                .provider(providerName)
                .providerId(userInfo.getProviderId())
                .build();
        socialAccountRepository.save(socialAccount);

        return newUser;
    }
}
