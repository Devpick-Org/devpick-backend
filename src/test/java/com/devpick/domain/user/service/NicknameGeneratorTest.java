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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NicknameGeneratorTest {

    @InjectMocks
    private NicknameGenerator nicknameGenerator;

    @Mock
    private UserRepository userRepository;

    // ── GitHub ──────────────────────────────────────────────

    @Test
    @DisplayName("GitHub name이 있고 중복 없으면 name을 그대로 반환한다")
    void generateFromGitHub_nameAvailable_returnsName() {
        GitHubUserInfo userInfo = new GitHubUserInfo("1", "loginuser", "a@test.com", "하영", null);
        given(userRepository.existsByNickname("하영")).willReturn(false);

        assertThat(nicknameGenerator.generateFromGitHub(userInfo)).isEqualTo("하영");
    }

    @Test
    @DisplayName("GitHub name이 null이면 login을 후보로 사용한다")
    void generateFromGitHub_nullName_usesLogin() {
        GitHubUserInfo userInfo = new GitHubUserInfo("2", "loginuser", "a@test.com", null, null);
        given(userRepository.existsByNickname("loginuser")).willReturn(false);

        assertThat(nicknameGenerator.generateFromGitHub(userInfo)).isEqualTo("loginuser");
    }

    @Test
    @DisplayName("GitHub name이 blank이면 login을 후보로 사용한다")
    void generateFromGitHub_blankName_usesLogin() {
        GitHubUserInfo userInfo = new GitHubUserInfo("3", "loginuser", "a@test.com", "  ", null);
        given(userRepository.existsByNickname("loginuser")).willReturn(false);

        assertThat(nicknameGenerator.generateFromGitHub(userInfo)).isEqualTo("loginuser");
    }

    @Test
    @DisplayName("GitHub 닉네임 중복 시 login + id suffix를 반환한다")
    void generateFromGitHub_duplicateNickname_returnsLoginWithSuffix() {
        GitHubUserInfo userInfo = new GitHubUserInfo("77777", "devhayoung", "dup@test.com", "하영", null);
        given(userRepository.existsByNickname("하영")).willReturn(true);

        assertThat(nicknameGenerator.generateFromGitHub(userInfo)).isEqualTo("devhayoung_77777");
    }

    // ── Google ──────────────────────────────────────────────

    @Test
    @DisplayName("Google name이 있고 중복 없으면 name을 그대로 반환한다")
    void generateFromGoogle_nameAvailable_returnsName() {
        GoogleUserInfo userInfo = new GoogleUserInfo("1", "a@gmail.com", "하영", null);
        given(userRepository.existsByNickname("하영")).willReturn(false);

        assertThat(nicknameGenerator.generateFromGoogle(userInfo)).isEqualTo("하영");
    }

    @Test
    @DisplayName("Google name이 null이면 email 앞부분을 후보로 사용한다")
    void generateFromGoogle_nullName_usesEmailPrefix() {
        GoogleUserInfo userInfo = new GoogleUserInfo("2", "devhayoung@gmail.com", null, null);
        given(userRepository.existsByNickname("devhayoung")).willReturn(false);

        assertThat(nicknameGenerator.generateFromGoogle(userInfo)).isEqualTo("devhayoung");
    }

    @Test
    @DisplayName("Google 닉네임 중복 시 emailPrefix + id suffix를 반환한다")
    void generateFromGoogle_duplicateNickname_returnsEmailPrefixWithSuffix() {
        GoogleUserInfo userInfo = new GoogleUserInfo("77777", "hayoung@gmail.com", "하영", null);
        given(userRepository.existsByNickname("하영")).willReturn(true);

        assertThat(nicknameGenerator.generateFromGoogle(userInfo)).isEqualTo("hayoung_77777");
    }
}
