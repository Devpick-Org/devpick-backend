package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("정상 회원가입 - 이메일, 비밀번호, 닉네임으로 가입 시 User가 저장된다")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "하영");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByNickname(request.nickname())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 중복 - 이미 사용 중인 이메일로 가입 시 AUTH_DUPLICATE_EMAIL 예외가 발생한다")
    void signup_duplicateEmail_throwsException() {
        // given
        SignupRequest request = new SignupRequest("duplicate@devpick.kr", "password123!", "하영");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("닉네임 중복 - 이미 사용 중인 닉네임으로 가입 시 AUTH_DUPLICATE_NICKNAME 예외가 발생한다")
    void signup_duplicateNickname_throwsException() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "중복닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByNickname(request.nickname())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_NICKNAME));
        verify(userRepository, never()).save(any(User.class));
    }
}
