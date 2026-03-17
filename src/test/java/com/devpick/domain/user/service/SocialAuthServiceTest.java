package com.devpick.domain.user.service;

import com.devpick.domain.user.client.OAuthProviderClient;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.SocialLoginResponse;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.domain.user.entity.SocialAccount;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.SocialAccountRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialAuthServiceTest {

    @Mock private OAuthProviderClient gitHubClient;
    @Mock private OAuthProviderClient googleClient;
    @Mock private OAuthStateService oAuthStateService;
    @Mock private NicknameGenerator nicknameGenerator;
    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private TokenService tokenService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        given(gitHubClient.getProviderName()).willReturn("github");
        given(googleClient.getProviderName()).willReturn("google");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        socialAuthService = new SocialAuthService(
                List.of(gitHubClient, googleClient),
                oAuthStateService, nicknameGenerator,
                userRepository, socialAccountRepository, tokenService, redisTemplate);
    }

    // ── generateAuthorizationUrl ──────────────────────────────────────────────

    @Test
    @DisplayName("GitHub 인가 URL 발급 시 state를 생성하고 GitHub 클라이언트 URL을 반환한다")
    void generateAuthorizationUrl_github_returnsUrlWithState() {
        given(oAuthStateService.generateState()).willReturn("test-state");
        given(gitHubClient.getAuthorizationUrl("test-state"))
                .willReturn("https://github.com/login/oauth/authorize?state=test-state");

        OAuthAuthorizationResponse response = socialAuthService.generateAuthorizationUrl("github");

        assertThat(response.authorizationUrl()).contains("state=test-state");
    }

    @Test
    @DisplayName("Google 인가 URL 발급 시 state를 생성하고 Google 클라이언트 URL을 반환한다")
    void generateAuthorizationUrl_google_returnsUrlWithState() {
        given(oAuthStateService.generateState()).willReturn("test-state");
        given(googleClient.getAuthorizationUrl("test-state"))
                .willReturn("https://accounts.google.com/o/oauth2/v2/auth?state=test-state");

        OAuthAuthorizationResponse response = socialAuthService.generateAuthorizationUrl("google");

        assertThat(response.authorizationUrl()).contains("state=test-state");
    }

    @Test
    @DisplayName("지원하지 않는 provider이면 AUTH_OAUTH_UNSUPPORTED_PROVIDER 예외가 발생한다")
    void generateAuthorizationUrl_unknownProvider_throwsException() {
        given(oAuthStateService.generateState()).willReturn("test-state");

        assertThatThrownBy(() -> socialAuthService.generateAuthorizationUrl("kakao"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_UNSUPPORTED_PROVIDER);
    }

    // ── login - GitHub (기존 계정) ──────────────────────────────────────────────

    @Test
    @DisplayName("기존 GitHub 소셜 계정이 있으면 isNewUser=false로 토큰을 발급한다")
    void login_github_existingSocialAccount_returnsIsNewUserFalse() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User existingUser = User.createSocialUser("hayoung@test.com", "하영");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(existingUser).provider("github").providerId("12345").build();
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", UUID.randomUUID(), "hayoung@test.com", "하영", false, "refresh-token");

        given(gitHubClient.exchangeToken("auth-code")).willReturn("github-access-token");
        given(gitHubClient.fetchUserInfo("github-access-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .willReturn(Optional.of(socialAccount));
        given(tokenService.issueTokenPairForSocial(existingUser, false)).willReturn(expected);

        SocialLoginResponse response = socialAuthService.login("github", "auth-code", "valid-state");

        assertThat(response.isNewUser()).isFalse();
        verify(userRepository, never()).save(any());
    }

    // ── login - GitHub (신규 계정) ──────────────────────────────────────────────

    @Test
    @DisplayName("신규 GitHub 소셜 계정이면 User와 SocialAccount를 생성하고 isNewUser=true를 반환한다")
    void login_github_newSocialAccount_createsUserAndReturnsIsNewUserTrue() {
        GitHubUserInfo userInfo = new GitHubUserInfo("99999", "newhayoung", "new@test.com", "New 하영", null);
        User newUser = User.createSocialUser("new@test.com", "New 하영");
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", UUID.randomUUID(), "new@test.com", "New 하영", true, "refresh-token");

        given(gitHubClient.exchangeToken("new-code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "99999"))
                .willReturn(Optional.empty());
        given(nicknameGenerator.generate(userInfo)).willReturn("New 하영");
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(tokenService.issueTokenPairForSocial(any(User.class), any(Boolean.class))).willReturn(expected);

        SocialLoginResponse response = socialAuthService.login("github", "new-code", "valid-state");

        assertThat(response.isNewUser()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    // ── login - GitHub 예외 ──────────────────────────────────────────────

    @Test
    @DisplayName("GitHub 사용자 이메일이 없으면 AUTH_SOCIAL_EMAIL_REQUIRED 예외가 발생한다")
    void login_github_nullEmail_throwsEmailRequiredException() {
        GitHubUserInfo userInfo = new GitHubUserInfo("55555", "noemail", null, "No Email", null);

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);

        assertThatThrownBy(() -> socialAuthService.login("github", "code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 사용자 이메일이 빈 문자열이면 AUTH_SOCIAL_EMAIL_REQUIRED 예외가 발생한다")
    void login_github_blankEmail_throwsEmailRequiredException() {
        GitHubUserInfo userInfo = new GitHubUserInfo("55556", "blankemail", "  ", "Blank", null);

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);

        assertThatThrownBy(() -> socialAuthService.login("github", "code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
    }

    // ── login - Google ──────────────────────────────────────────────

    @Test
    @DisplayName("기존 Google 소셜 계정이 있으면 isNewUser=false로 토큰을 발급한다")
    void login_google_existingSocialAccount_returnsIsNewUserFalse() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "hayoung@gmail.com", "하영", null);
        User existingUser = User.createSocialUser("hayoung@gmail.com", "하영");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(existingUser).provider("google").providerId("google-123").build();
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", UUID.randomUUID(), "hayoung@gmail.com", "하영", false, "refresh-token");

        given(googleClient.exchangeToken("google-code")).willReturn("google-access-token");
        given(googleClient.fetchUserInfo("google-access-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("google", "google-123"))
                .willReturn(Optional.of(socialAccount));
        given(tokenService.issueTokenPairForSocial(existingUser, false)).willReturn(expected);

        SocialLoginResponse response = socialAuthService.login("google", "google-code", "valid-state");

        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("Google 사용자 이메일이 없으면 AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED 예외가 발생한다")
    void login_google_nullEmail_throwsGoogleEmailRequiredException() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-456", null, "No Email", null);

        given(googleClient.exchangeToken("code")).willReturn("google-token");
        given(googleClient.fetchUserInfo("google-token")).willReturn(userInfo);

        assertThatThrownBy(() -> socialAuthService.login("google", "code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED);
    }

    // ── login - 탈퇴 계정 처리 (소셜 계정 기존 존재) ──────────────────────────────────────────────

    @Test
    @DisplayName("소셜 로그인 — 기존 소셜 계정의 유저가 탈퇴 후 7일 이내이면 AUTH_ACCOUNT_RECOVERABLE 예외와 recoveryToken을 반환한다")
    void login_existingAccountRecoverable_throwsRecoverableWithToken() throws Exception {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User deletedUser = User.createVerifiedEmailUser("hayoung@test.com", "encoded", "하영");
        // @UuidGenerator는 persist 시점에 생성되므로 테스트에서 직접 설정
        java.lang.reflect.Field idField = com.devpick.global.entity.BaseTimeEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(deletedUser, UUID.randomUUID());
        deletedUser.softDelete();
        SocialAccount socialAccount = SocialAccount.builder()
                .user(deletedUser).provider("github").providerId("12345").build();

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .willReturn(Optional.of(socialAccount));

        // when & then
        assertThatThrownBy(() -> socialAuthService.login("github", "code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_RECOVERABLE);

        assertThat(deletedUser.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("소셜 로그인 — 기존 소셜 계정의 유저가 탈퇴 후 7일 경과이면 익명화 후 신규 계정으로 로그인된다")
    void login_existingAccountExpired_anonymizesAndCreatesNewUser() throws Exception {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        User expiredUser = User.createVerifiedEmailUser("hayoung@test.com", "encoded", "하영");
        expiredUser.softDelete();
        java.lang.reflect.Field field = User.class.getDeclaredField("deletedAt");
        field.setAccessible(true);
        field.set(expiredUser, java.time.LocalDateTime.now().minusDays(8));
        SocialAccount socialAccount = SocialAccount.builder()
                .user(expiredUser).provider("github").providerId("12345").build();
        User newUser = User.createSocialUser("hayoung@test.com", "하영2");
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", UUID.randomUUID(), "hayoung@test.com", "하영2", true, "refresh-token");

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "12345"))
                .willReturn(Optional.of(socialAccount));
        given(userRepository.findByEmail("hayoung@test.com")).willReturn(Optional.empty());
        given(nicknameGenerator.generate(userInfo)).willReturn("하영2");
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(tokenService.issueTokenPairForSocial(any(User.class), any(Boolean.class))).willReturn(expected);

        // when
        SocialLoginResponse response = socialAuthService.login("github", "code", "valid-state");

        // then
        assertThat(expiredUser.getEmail()).startsWith("deleted_");
        assertThat(response.isNewUser()).isTrue();
        verify(socialAccountRepository).delete(socialAccount);
    }

    // ── login - 탈퇴 계정 처리 (소셜 계정 신규, 이메일 기존 존재) ──────────────────────────────────────────────

    @Test
    @DisplayName("소셜 신규 가입 — 이메일 탈퇴 계정이 7일 이내면 AUTH_ACCOUNT_RECOVERABLE 예외가 발생한다")
    void registerNewSocialUser_recoverableEmailExists_throwsRecoverable() {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("99999", "hayoung", "hayoung@test.com", "하영", null);
        User recoverableUser = User.createVerifiedEmailUser("hayoung@test.com", "encoded", "하영");
        recoverableUser.softDelete();

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "99999"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("hayoung@test.com")).willReturn(Optional.of(recoverableUser));

        // when & then
        assertThatThrownBy(() -> socialAuthService.login("github", "code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_RECOVERABLE);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("소셜 신규 가입 — 이메일 탈퇴 계정이 7일 경과이면 익명화 후 신규 가입된다")
    void registerNewSocialUser_expiredEmailExists_anonymizesAndCreatesNew() throws Exception {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("99998", "hayoung", "hayoung@test.com", "하영", null);
        User expiredUser = User.createVerifiedEmailUser("hayoung@test.com", "encoded", "하영");
        expiredUser.softDelete();
        java.lang.reflect.Field field = User.class.getDeclaredField("deletedAt");
        field.setAccessible(true);
        field.set(expiredUser, java.time.LocalDateTime.now().minusDays(8));
        User newUser = User.createSocialUser("hayoung@test.com", "하영2");
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", UUID.randomUUID(), "hayoung@test.com", "하영2", true, "refresh-token");

        given(gitHubClient.exchangeToken("code")).willReturn("github-token");
        given(gitHubClient.fetchUserInfo("github-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId("github", "99998"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("hayoung@test.com")).willReturn(Optional.of(expiredUser));
        given(nicknameGenerator.generate(userInfo)).willReturn("하영2");
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(tokenService.issueTokenPairForSocial(any(User.class), any(Boolean.class))).willReturn(expected);

        // when
        SocialLoginResponse response = socialAuthService.login("github", "code", "valid-state");

        // then
        assertThat(expiredUser.getEmail()).startsWith("deleted_");
        assertThat(response.isNewUser()).isTrue();
    }

    // ── recoverWithToken ──────────────────────────────────────────────

    @Test
    @DisplayName("recoverWithToken — 유효한 토큰이면 계정을 복구하고 JWT를 발급한다")
    void recoverWithToken_validToken_reactivatesAndReturnsToken() throws Exception {
        // given
        String recoveryToken = "valid-token";
        User deletedUser = User.createVerifiedEmailUser("hayoung@test.com", "encoded", "하영");
        java.lang.reflect.Field idField = com.devpick.global.entity.BaseTimeEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        UUID userId = UUID.randomUUID();
        idField.set(deletedUser, userId);
        deletedUser.softDelete();
        SocialLoginResponse expected = new SocialLoginResponse(
                "access-token", userId, "hayoung@test.com", "하영", false, "refresh-token");

        given(redisTemplate.opsForValue().get("social:recover:" + recoveryToken)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(deletedUser));
        given(tokenService.issueTokenPairForSocial(deletedUser, false)).willReturn(expected);

        // when
        SocialLoginResponse response = socialAuthService.recoverWithToken(recoveryToken);

        // then
        assertThat(deletedUser.getIsActive()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(redisTemplate).delete("social:recover:" + recoveryToken);
    }

    @Test
    @DisplayName("recoverWithToken — Redis에 토큰이 없으면 AUTH_ACCOUNT_DELETED 예외가 발생한다")
    void recoverWithToken_tokenNotFound_throwsDeleted() {
        given(redisTemplate.opsForValue().get(any())).willReturn(null);

        assertThatThrownBy(() -> socialAuthService.recoverWithToken("expired-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_DELETED);
    }

    // ── state 검증 ──────────────────────────────────────────────

    @Test
    @DisplayName("유효하지 않은 state이면 AUTH_INVALID_STATE 예외가 발생하고 OAuth API를 호출하지 않는다")
    void login_invalidState_throwsInvalidStateException() {
        doThrow(new DevpickException(ErrorCode.AUTH_INVALID_STATE))
                .when(oAuthStateService).validateAndDeleteState("invalid-state");

        assertThatThrownBy(() -> socialAuthService.login("github", "code", "invalid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_STATE);

        verify(gitHubClient, never()).exchangeToken(anyString());
    }
}
