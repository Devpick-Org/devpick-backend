package com.devpick.domain.user.service;

import com.devpick.domain.user.client.GoogleOAuthClient;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private OAuthStateService oAuthStateService;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private static final String GOOGLE_PROVIDER = "google";

    // ── 정상 케이스 ──────────────────────────────────────────────

    @Test
    @DisplayName("기존 Google 소셜 계정이 있으면 isNewUser=false로 토큰을 발급한다")
    void login_existingSocialAccount_returnsTokensWithIsNewUserFalse() {
        // given
        String code = "auth-code-123";
        GoogleUserInfo userInfo = new GoogleUserInfo("12345", "hayoung@gmail.com", "하영", null);
        User existingUser = User.createSocialUser("hayoung@gmail.com", "하영");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(existingUser)
                .provider(GOOGLE_PROVIDER)
                .providerId("12345")
                .build();

        given(googleOAuthClient.exchangeToken(code)).willReturn("google-access-token");
        given(googleOAuthClient.fetchUserInfo("google-access-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, "12345"))
                .willReturn(Optional.of(socialAccount));
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = googleAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.isNewUser()).isFalse();
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 Google 소셜 계정이면 User와 SocialAccount를 생성하고 isNewUser=true를 반환한다")
    void login_newSocialAccount_createsUserAndReturnsIsNewUserTrue() {
        // given
        String code = "new-auth-code";
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "new@gmail.com", "New 하영", null);
        User newUser = User.createSocialUser("new@gmail.com", "New 하영");

        given(googleOAuthClient.exchangeToken(code)).willReturn("google-access-token");
        given(googleOAuthClient.fetchUserInfo("google-access-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, "99999"))
                .willReturn(Optional.empty());
        given(nicknameGenerator.generateFromGoogle(userInfo)).willReturn("New 하영");
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        LoginResponse response = googleAuthService.login(code, "valid-state");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.isNewUser()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    // ── 예외 케이스 ──────────────────────────────────────────────

    @Test
    @DisplayName("Google 사용자 이메일이 null이면 AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED 예외가 발생한다")
    void login_nullEmail_throwsEmailRequiredException() {
        // given
        GoogleUserInfo userInfo = new GoogleUserInfo("55555", null, "No Email", null);

        given(googleOAuthClient.exchangeToken("code")).willReturn("google-token");
        given(googleOAuthClient.fetchUserInfo("google-token")).willReturn(userInfo);

        // when & then
        assertThatThrownBy(() -> googleAuthService.login("code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Google 사용자 이메일이 빈 문자열이면 AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED 예외가 발생한다")
    void login_blankEmail_throwsEmailRequiredException() {
        // given
        GoogleUserInfo userInfo = new GoogleUserInfo("55556", "  ", "Blank Email", null);

        given(googleOAuthClient.exchangeToken("code")).willReturn("google-token");
        given(googleOAuthClient.fetchUserInfo("google-token")).willReturn(userInfo);

        // when & then
        assertThatThrownBy(() -> googleAuthService.login("code", "valid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED);
    }

    @Test
    @DisplayName("Refresh Token을 저장하고 기존 Refresh Token은 삭제한다")
    void login_savesNewRefreshTokenAndDeletesOldOne() {
        // given
        GoogleUserInfo userInfo = new GoogleUserInfo("11111", "token@gmail.com", "토큰테스트", null);
        User existingUser = User.createSocialUser("token@gmail.com", "토큰테스트");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(existingUser).provider(GOOGLE_PROVIDER).providerId("11111").build();

        given(googleOAuthClient.exchangeToken("code")).willReturn("google-token");
        given(googleOAuthClient.fetchUserInfo("google-token")).willReturn(userInfo);
        given(socialAccountRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, "11111"))
                .willReturn(Optional.of(socialAccount));
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        // when
        googleAuthService.login("code", "valid-state");

        // then
        verify(refreshTokenRepository).deleteByUser(existingUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── state 파라미터 검증 (DP-284) ────────────────────────────────

    @Test
    @DisplayName("유효하지 않은 state이면 AUTH_INVALID_STATE 예외가 발생하고 Google API를 호출하지 않는다")
    void login_invalidState_throwsInvalidStateException() {
        // given
        doThrow(new DevpickException(ErrorCode.AUTH_INVALID_STATE))
                .when(oAuthStateService).validateAndDeleteState("invalid-state");

        // when & then
        assertThatThrownBy(() -> googleAuthService.login("code", "invalid-state"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_STATE);

        verify(googleOAuthClient, never()).exchangeToken(anyString());
    }

    @Test
    @DisplayName("generateAuthorizationUrl은 OAuthStateService에서 state를 생성하고 Google 클라이언트 URL을 반환한다")
    void generateAuthorizationUrl_returnsUrlWithState() {
        // given
        given(oAuthStateService.generateState()).willReturn("test-state-uuid");
        given(googleOAuthClient.getAuthorizationUrl("test-state-uuid"))
                .willReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=xxx&state=test-state-uuid");

        // when
        OAuthAuthorizationResponse response = googleAuthService.generateAuthorizationUrl();

        // then
        assertThat(response.authorizationUrl()).contains("state=test-state-uuid");
    }
}
