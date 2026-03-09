package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * GoogleAuthService는 SocialAuthService 위임 래퍼(@Deprecated)입니다.
 * 실제 비즈니스 로직은 SocialAuthServiceTest에서 검증하며,
 * 이 테스트는 위임 호출이 올바르게 이루어지는지만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Mock
    private SocialAuthService socialAuthService;

    @Test
    @DisplayName("GoogleAuthService.login()은 SocialAuthService.login('google', code)에 위임한다")
    void login_delegatesToSocialAuthService() {
        // given
        String code = "google-auth-code";
        LoginResponse expected = new LoginResponse(
                "access-token", "refresh-token", UUID.randomUUID(), "hayoung@gmail.com", "하영");
        given(socialAuthService.login(eq("google"), eq(code))).willReturn(expected);

        // when
        LoginResponse result = googleAuthService.login(code);

        // then
        assertThat(result).isEqualTo(expected);
        verify(socialAuthService).login("google", code);
    }
}
