package com.devpick.domain.user.service;

import com.devpick.domain.user.client.OAuthProviderClient;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private NicknameGenerator nicknameGenerator;

    private OAuthProviderClient githubClient;
    private OAuthProviderClient googleClient;

    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        githubClient = mock(OAuthProviderClient.class);
        when(githubClient.getProviderName()).thenReturn("github");

        googleClient = mock(OAuthProviderClient.class);
        when(googleClient.getProviderName()).thenReturn("google");

        socialAuthService = new SocialAuthService(
                List.of(githubClient, googleClient),
                userRepository,
                socialAccountRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                nicknameGenerator
        );
    }

    // ── GitHub 로그인 ──────────────────────────────────────────────

    @Test
    @DisplayName("GitHub 로그인 - 기존 소셜 계정이 있으면 신규 생성 없이 JWT를 발급한다")
    void login_github_existingSocialAccount_returnsTokens() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User existingUser = buildUser("hayoung@test.com", "hayoung");
        SocialAccount existingAccount = SocialAccount.builder()
                .user(existingUser).provider("github").providerId("12345").build();

        when(githubClient.exchangeToken("code")).thenReturn("github-token");
        when(githubClient.fetchUserInfo("github-token")).thenReturn(userInfo);
        when(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.of(existingAccount));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));

        LoginResponse response = socialAuthService.login("github", "code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 로그인 - 신규 사용자이면 User + SocialAccount를 생성하고 JWT를 발급한다")
    void login_github_newUser_createsUserAndTokens() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User newUser = buildUser("hayoung@test.com", "하영");

        when(githubClient.exchangeToken("code")).thenReturn("github-token");
        when(githubClient.fetchUserInfo("github-token")).thenReturn(userInfo);
        when(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.empty());
        when(nicknameGenerator.generate(any(), any())).thenReturn("하영");
        when(userRepository.save(any())).thenReturn(newUser);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));

        LoginResponse response = socialAuthService.login("github", "code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("GitHub 로그인 - 이메일이 null이면 AUTH_SOCIAL_EMAIL_REQUIRED 예외 발생")
    void login_github_nullEmail_throwsEmailRequired() {
        GitHubUserInfo userInfoNoEmail = new GitHubUserInfo("12345", "hayoung", null, "하영", null);

        when(githubClient.exchangeToken("code")).thenReturn("github-token");
        when(githubClient.fetchUserInfo("github-token")).thenReturn(userInfoNoEmail);
        when(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAuthService.login("github", "code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
    }

    // ── Google 로그인 ──────────────────────────────────────────────

    @Test
    @DisplayName("Google 로그인 - 기존 소셜 계정이 있으면 신규 생성 없이 JWT를 발급한다")
    void login_google_existingSocialAccount_returnsTokens() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        User existingUser = buildUser("hayoung@gmail.com", "hayoung");
        SocialAccount existingAccount = SocialAccount.builder()
                .user(existingUser).provider("google").providerId("99999").build();

        when(googleClient.exchangeToken("code")).thenReturn("google-token");
        when(googleClient.fetchUserInfo("google-token")).thenReturn(userInfo);
        when(socialAccountRepository.findByProviderAndProviderId("google", "99999"))
                .thenReturn(Optional.of(existingAccount));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));

        LoginResponse response = socialAuthService.login("google", "code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Google 로그인 - 신규 사용자이면 User + SocialAccount를 생성한다")
    void login_google_newUser_createsUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        User newUser = buildUser("hayoung@gmail.com", "hayoung");

        when(googleClient.exchangeToken("code")).thenReturn("google-token");
        when(googleClient.fetchUserInfo("google-token")).thenReturn(userInfo);
        when(socialAccountRepository.findByProviderAndProviderId("google", "99999"))
                .thenReturn(Optional.empty());
        when(nicknameGenerator.generate(any(), any())).thenReturn("hayoung");
        when(userRepository.save(any())).thenReturn(newUser);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));

        socialAuthService.login("google", "code");

        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    // ── 공통 에러 케이스 ──────────────────────────────────────────────

    @Test
    @DisplayName("지원하지 않는 provider이면 AUTH_OAUTH_UNSUPPORTED_PROVIDER 예외 발생")
    void login_unsupportedProvider_throwsException() {
        assertThatThrownBy(() -> socialAuthService.login("kakao", "code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_UNSUPPORTED_PROVIDER);
    }

    @Test
    @DisplayName("client가 DevpickException을 던지면 그대로 전파된다")
    void login_clientThrowsDevpickException_propagates() {
        when(githubClient.exchangeToken(anyString()))
                .thenThrow(new DevpickException(ErrorCode.AUTH_OAUTH_CODE_EXPIRED));

        assertThatThrownBy(() -> socialAuthService.login("github", "expired"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_CODE_EXPIRED);
    }

    // ── helper ──────────────────────────────────────────────

    private User buildUser(String email, String nickname) {
        return User.createSocialUser(email, nickname);
    }
}
