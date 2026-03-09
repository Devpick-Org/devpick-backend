package com.devpick.domain.user.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthUserInfoTest {

    @Test
    @DisplayName("GitHubUserInfo - getProviderId()는 id를 반환한다")
    void githubUserInfo_getProviderId() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        assertThat(userInfo.getProviderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("GitHubUserInfo - getEmail()은 email을 반환한다")
    void githubUserInfo_getEmail() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        assertThat(userInfo.getEmail()).isEqualTo("hayoung@test.com");
    }

    @Test
    @DisplayName("GitHubUserInfo - getNicknamePrefix()는 login을 반환한다")
    void githubUserInfo_getNicknamePrefix_returnsLogin() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        assertThat(userInfo.getNicknamePrefix()).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("GoogleUserInfo - getProviderId()는 id를 반환한다")
    void googleUserInfo_getProviderId() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        assertThat(userInfo.getProviderId()).isEqualTo("99999");
    }

    @Test
    @DisplayName("GoogleUserInfo - getEmail()은 email을 반환한다")
    void googleUserInfo_getEmail() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        assertThat(userInfo.getEmail()).isEqualTo("hayoung@gmail.com");
    }

    @Test
    @DisplayName("GoogleUserInfo - getNicknamePrefix()는 email @ 앞부분을 반환한다")
    void googleUserInfo_getNicknamePrefix_returnsEmailPrefix() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "hayoung@gmail.com", "하영", null);
        assertThat(userInfo.getNicknamePrefix()).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("GoogleUserInfo - email이 null이면 getNicknamePrefix()는 null을 반환한다")
    void googleUserInfo_getNicknamePrefix_nullEmail() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", null, "하영", null);
        assertThat(userInfo.getNicknamePrefix()).isNull();
    }

    @Test
    @DisplayName("GoogleUserInfo - email에 @가 없으면 getNicknamePrefix()는 email 전체를 반환한다")
    void googleUserInfo_getNicknamePrefix_noAtSign() {
        GoogleUserInfo userInfo = new GoogleUserInfo("99999", "invalidemail", "하영", null);
        assertThat(userInfo.getNicknamePrefix()).isEqualTo("invalidemail");
    }
}
