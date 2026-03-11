package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NicknameGeneratorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NicknameGenerator nicknameGenerator;

    @Test
    @DisplayName("name이 있고 중복이 없으면 name을 반환한다")
    void generate_nameAvailable_returnsName() {
        when(userRepository.existsByNickname("하영")).thenReturn(false);

        String result = nicknameGenerator.generate("하영", "hayoung", "12345");

        assertThat(result).isEqualTo("하영");
    }

    @Test
    @DisplayName("name이 중복이면 prefix를 시도한다")
    void generate_nameDuplicated_returnsPrefix() {
        when(userRepository.existsByNickname("하영")).thenReturn(true);
        when(userRepository.existsByNickname("hayoung")).thenReturn(false);

        String result = nicknameGenerator.generate("하영", "hayoung", "12345");

        assertThat(result).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("name, prefix 모두 중복이면 prefix_providerId suffix를 반환한다")
    void generate_bothDuplicated_returnsSuffix() {
        when(userRepository.existsByNickname("하영")).thenReturn(true);
        when(userRepository.existsByNickname("hayoung")).thenReturn(true);

        String result = nicknameGenerator.generate("하영", "hayoung", "12345");

        assertThat(result).isEqualTo("hayoung_12345");
    }

    @Test
    @DisplayName("name이 null이면 prefix를 바로 시도한다")
    void generate_nameNull_triesPrefix() {
        when(userRepository.existsByNickname("hayoung")).thenReturn(false);

        String result = nicknameGenerator.generate(null, "hayoung", "12345");

        assertThat(result).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("name이 blank이면 prefix를 바로 시도한다")
    void generate_nameBlank_triesPrefix() {
        when(userRepository.existsByNickname("hayoung")).thenReturn(false);

        String result = nicknameGenerator.generate("  ", "hayoung", "12345");

        assertThat(result).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("GitHubUserInfo OAuthUserInfo 오버로드 - getNicknamePrefix()는 login이다")
    void generate_githubUserInfo_usesLogin() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        when(userRepository.existsByNickname("hayoung")).thenReturn(false);

        String result = nicknameGenerator.generate(null, userInfo);

        assertThat(result).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("GoogleUserInfo OAuthUserInfo 오버로드 - getNicknamePrefix()는 email @ 앞부분이다")
    void generate_googleUserInfo_usesEmailPrefix() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        when(userRepository.existsByNickname("하영")).thenReturn(true); // name 중복
        when(userRepository.existsByNickname("hayoung")).thenReturn(false); // email prefix 사용

        String result = nicknameGenerator.generate("하영", userInfo);

        assertThat(result).isEqualTo("hayoung");
    }
}
