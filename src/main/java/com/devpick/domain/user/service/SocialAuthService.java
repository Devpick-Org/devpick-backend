package com.devpick.domain.user.service;

import com.devpick.domain.user.client.OAuthProviderClient;
import com.devpick.domain.user.dto.SocialLoginResponse;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.OAuthUserInfo;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 로그인 통합 서비스 (DP-183, DP-184, DP-284).
 *
 * Strategy Pattern — OAuthProviderClient 인터페이스를 통해 GitHub/Google을 동일하게 처리한다.
 * List<OAuthProviderClient>를 주입받아 getProviderName()으로 라우팅하므로,
 * 새 OAuth 제공자 추가 시 OAuthProviderClient 구현체만 추가하면 된다 (OCP 준수).
 *
 * 공통 로직:
 * 1. state 검증 (CSRF 방지) → Redis에서 확인 후 삭제
 * 2. 인가 코드 → provider Access Token 교환
 * 3. provider 사용자 정보 조회 + 이메일 검증
 * 4. 기존 소셜 계정 조회 or 신규 User + SocialAccount 생성
 * 5. JWT 발급 — TokenService 위임 (DRY)
 * 6. isNewUser 플래그를 응답에 포함 (DP-284)
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private static final String RECOVER_KEY_PREFIX = "social:recover:";
    private static final Duration RECOVER_TTL = Duration.ofMinutes(10);

    private final List<OAuthProviderClient> oAuthProviderClients;
    private final OAuthStateService oAuthStateService;
    private final NicknameGenerator nicknameGenerator;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;

    /**
     * OAuth 인가 URL 발급.
     * state UUID를 Redis에 저장(5분)하고 인가 URL에 포함.
     */
    public OAuthAuthorizationResponse generateAuthorizationUrl(String provider) {
        String state = oAuthStateService.generateState();
        return new OAuthAuthorizationResponse(findClient(provider).getAuthorizationUrl(state));
    }

    /**
     * 소셜 로그인 처리.
     */
    @Transactional
    public SocialLoginResponse login(String provider, String code, String state) {
        oAuthStateService.validateAndDeleteState(state);

        OAuthProviderClient client = findClient(provider);
        String providerAccessToken = client.exchangeToken(code);
        OAuthUserInfo userInfo = client.fetchUserInfo(providerAccessToken);

        validateEmail(userInfo.getEmail(), provider);

        Optional<SocialAccount> existingAccount =
                socialAccountRepository.findByProviderAndProviderId(provider, userInfo.getProviderId());

        User user;
        boolean isNewUser;

        if (existingAccount.isPresent()) {
            user = existingAccount.get().getUser();
            isNewUser = false;
            if (!user.getIsActive()) {
                if (user.isRecoverable()) {
                    String recoveryToken = UUID.randomUUID().toString();
                    redisTemplate.opsForValue().set(RECOVER_KEY_PREFIX + recoveryToken,
                            user.getId().toString(), RECOVER_TTL);
                    throw new DevpickException(ErrorCode.AUTH_ACCOUNT_RECOVERABLE,
                            Map.of("deletedAt", user.getDeletedAt(), "recoveryToken", recoveryToken));
                } else {
                    user.anonymize();
                    socialAccountRepository.delete(existingAccount.get());
                    user = registerNewSocialUser(provider, userInfo);
                    isNewUser = true;
                }
            }
        } else {
            user = registerNewSocialUser(provider, userInfo);
            isNewUser = true;
        }

        return tokenService.issueTokenPairForSocial(user, isNewUser);
    }

    /**
     * 소셜 로그인 탈퇴 계정 복구.
     * 콜백에서 발급된 임시 recoveryToken(Redis, TTL 10분)으로 계정을 복구하고 JWT를 발급한다.
     */
    @Transactional
    public SocialLoginResponse recoverWithToken(String recoveryToken) {
        String key = RECOVER_KEY_PREFIX + recoveryToken;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new DevpickException(ErrorCode.AUTH_ACCOUNT_DELETED);
        }
        redisTemplate.delete(key);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND));

        if (!user.isRecoverable()) {
            throw new DevpickException(ErrorCode.AUTH_ACCOUNT_DELETED);
        }

        user.reactivate();
        return tokenService.issueTokenPairForSocial(user, false);
    }

    private User registerNewSocialUser(String provider, OAuthUserInfo userInfo) {
        // 같은 이메일의 탈퇴 계정이 있으면 처리 (이메일 가입 후 소셜 로그인 시도 등)
        userRepository.findByEmail(userInfo.getEmail()).ifPresent(existing -> {
            if (!existing.getIsActive()) {
                if (existing.isRecoverable()) {
                    throw new DevpickException(ErrorCode.AUTH_ACCOUNT_RECOVERABLE);
                }
                existing.anonymize();
            }
        });

        String nickname = nicknameGenerator.generate(userInfo);
        User newUser = User.createSocialUser(userInfo.getEmail(), nickname);
        userRepository.save(newUser);

        socialAccountRepository.save(SocialAccount.builder()
                .user(newUser)
                .provider(provider)
                .providerId(userInfo.getProviderId())
                .build());

        return newUser;
    }

    private void validateEmail(String email, String provider) {
        if (email == null || email.isBlank()) {
            throw new DevpickException(resolveEmailErrorCode(provider));
        }
    }

    private ErrorCode resolveEmailErrorCode(String provider) {
        return switch (provider) {
            case "google" -> ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED;
            default       -> ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED;
        };
    }

    private OAuthProviderClient findClient(String provider) {
        return oAuthProviderClients.stream()
                .filter(client -> client.getProviderName().equals(provider))
                .findFirst()
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_OAUTH_UNSUPPORTED_PROVIDER));
    }
}
