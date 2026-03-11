package com.devpick.domain.user.service;

import com.devpick.domain.user.client.GitHubOAuthClient;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GitHubAuthServiceTest {

    @InjectMocks
    private GitHubAuthService gitHubAuthService;

    @Mock
    private GitHubOAuthClient gitHubOAuthClient;

    @Mock
    private OAuthStateService oAuthStateService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private static final String GITHUB_PROVIDER = "github";

    // ── 정상 케이스 ──────────────────────────────────────────────

    @Test
    @DisplayName("기존 GitHub 소셜 계정이 있으면 신규 회원 생성 없이 토큰을 발급한다")
    void login_existingSocialAccount_returnsTokensWithoutCreatingNewUser() {
        // given
        String code = "auth-code-123";
        String githubAccessToken = "github-access-token";
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User existingUser = User.createSocialUser("hayoung@test.com", "하영");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(existingUser)
                .provider(GITHUB_PROVIDER)
                .providerId("12345")
                .build();

        given(gitHubOAuthClient.exchangeToken(code)).willReturn(githubAccessToken);
        given(gitHubOAuthClient.fetchUserInfo(githubAccessToken)).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "12345"))
                .willReturn(Optional.of(socialAccount));
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = gitHubAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 GitHub 소셜 계정이면 User와 SocialAccount를 생성하고 토큰을 발급한다")
    void login_newSocialAccount_createsUserAndSocialAccountThenReturnsTokens() {
        // given
        String code = "new-auth-code";
        String githubAccessToken = "github-access-token";
        GitHubUserInfo userInfo = new GitHubUserInfo("99999", "newhayoung", "new@test.com", "New 하영", null);
        User newUser = User.createSocialUser("new@test.com", "New 하영");

        given(gitHubOAuthClient.exchangeToken(code)).willReturn(githubAccessToken);
        given(gitHubOAuthClient.fetchUserInfo(githubAccessToken)).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "99999"))
                .willReturn(Optional.empty());
        given(userRepository.existsByNickname("New 하영")).willReturn(false);
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = gitHubAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("닉네임이 중복이면 login + id suffix로 닉네임을 생성한다")
    void login_duplicateNickname_useLoginWithSuffix() {
        // given
        String code = "code";
        GitHubUserInfo userInfo = new GitHubUserInfo("77777", "devhayoung", "dup@test.com", "하영", null);
        User newUser = User.createSocialUser("dup@test.com", "devhayoung_77777");

        given(gitHubOAuthClient.exchangeToken(code)).willReturn("github-token");
        given(gitHubOAuthClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "77777"))
                .willReturn(Optional.empty());
        given(userRepository.existsByNickname("하영")).willReturn(true);      // name 중복
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = gitHubAuthService.login(code, "valid-state");

        // then
        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    // ── 예외 케이스 ──────────────────────────────────────────────

    @Test
    @DisplayName("GitHub 사용자 이메일이 없으면 AUTH_SOCIAL_EMAIL_REQUIRED 예외가 발생한다")
    void login_nullEmail_throwsEmailRequiredException() {
        // given
        String code = "code";
        GitHubUserInfo userInfo = new GitHubUserInfo("55555", "noemail", null, "No Email", null);

        given(gitHubOAuthClient.exchangeToken(code)).willReturn("github-token");
        given(gitHubOAuthClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "55555"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gitHubAuthService.login(code, "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 사용자 이메일이 빈 문자열이면 AUTH_SOCIAL_EMAIL_REQUIRED 예외가 발생한다")
    void login_blankEmail_throwsEmailRequiredException() {
        // given
        String code = "code";
        GitHubUserInfo userInfo = new GitHubUserInfo("55556", "blankemail", "  ", "Blank Email", null);

        given(gitHubOAuthClient.exchangeToken(code)).willReturn("github-token");
        given(gitHubOAuthClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "55556"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gitHubAuthService.login(code, "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
    }

    @Test
    @DisplayName("GitHub name이 null이면 login을 닉네임 후보로 사용한다")
    void login_nullName_usesLoginAsNickname() {
        // given — name=null, login=사용, 중복 없음
        String code = "code";
        GitHubUserInfo userInfo = new GitHubUserInfo("88888", "loginuser", "login@test.com", null, null);
        User newUser = User.createSocialUser("login@test.com", "loginuser");

        given(gitHubOAuthClient.exchangeToken(code)).willReturn("github-token");
        given(gitHubOAuthClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "88888"))
                .willReturn(Optional.empty());
        given(userRepository.existsByNickname("loginuser")).willReturn(false);
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = gitHubAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("GitHub name이 빈 문자열이면 login을 닉네임 후보로 사용한다")
    void login_blankName_usesLoginAsNickname() {
        // given — name="  "(blank), login 사용
        String code = "code";
        GitHubUserInfo userInfo = new GitHubUserInfo("11111", "blankname", "blank@test.com", "  ", null);
        User newUser = User.createSocialUser("blank@test.com", "blankname");

        given(gitHubOAuthClient.exchangeToken(code)).willReturn("github-token");
        given(gitHubOAuthClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GITHUB_PROVIDER, "11111"))
                .willReturn(Optional.empty());
        given(userRepository.existsByNickname("blankname")).willReturn(false);
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = gitHubAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
    }

    // ── state 파라미터 검증 (DP-284) ──────────────────────────────────────────────

    @Test
    @DisplayName("유효하지 않은 state이면 AUTH_INVALID_STATE 예외가 발생하고 GitHub API를 호출하지 않는다")
    void login_invalidState_throwsInvalidStateException() {
        // given
        doThrow(new DevpickException(ErrorCode.AUTH_INVALID_STATE))
                .when(oAuthStateService).validateAndDeleteState("invalid-state");

        // when & then
        assertThatThrownBy(() -> gitHubAuthService.login("code", "invalid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_STATE);

        verify(gitHubOAuthClient, never()).exchangeToken(anyString());
    }

    @Test
    @DisplayName("generateAuthorizationUrl은 OAuthStateService에서 state를 생성하고 GitHub 클라이언트 URL을 반환한다")
    void generateAuthorizationUrl_returnsUrlWithState() {
        // given
        given(oAuthStateService.generateState()).willReturn("test-state-uuid");
        given(gitHubOAuthClient.getAuthorizationUrl("test-state-uuid"))
                .willReturn("https://github.com/login/oauth/authorize?client_id=xxx&state=test-state-uuid");

        // when
        OAuthAuthorizationResponse response = gitHubAuthService.generateAuthorizationUrl();

        // then
        assertThat(response.authorizationUrl()).contains("state=test-state-uuid");
    }
}
