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
 * GitHubAuthService는 SocialAuthService 위임 래퍼(@Deprecated)입니다.
 * 실제 비즈니스 로직은 SocialAuthServiceTest에서 검증하며,
 * 이 테스트는 위임 호출이 올바르게 이루어지는지만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class GitHubAuthServiceTest {

    @InjectMocks
    private GitHubAuthService gitHubAuthService;

    @Mock
    private SocialAuthService socialAuthService;

    @Test
    @DisplayName("GitHubAuthService.login()은 SocialAuthService.login('github', code)에 위임한다")
    void login_delegatesToSocialAuthService() {
        // given
        String code = "github-auth-code";
        LoginResponse expected = new LoginResponse(
                "access-token", "refresh-token", UUID.randomUUID(), "hayoung@test.com", "하영");
        given(socialAuthService.login(eq("github"), eq(code))).willReturn(expected);

        // when
        LoginResponse result = gitHubAuthService.login(code);

        // then
        assertThat(result).isEqualTo(expected);
        verify(socialAuthService).login("github", code);
    }
}
