package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GitHubTokenResponse;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class GitHubOAuthClientTest {

    private GitHubOAuthClient gitHubOAuthClient;

    // WebClient 체이닝: raw type으로 제네릭 캡처 에러 방지
    private WebClient webClient;
    private WebClient.RequestBodyUriSpec postUriSpec;
    private WebClient.RequestBodySpec postBodySpec;
    private WebClient.RequestHeadersSpec postHeadersSpec;
    private WebClient.RequestHeadersUriSpec getUriSpec;
    private WebClient.RequestHeadersSpec getHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        postUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        postBodySpec = mock(WebClient.RequestBodySpec.class);
        postHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        getUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        getHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        gitHubOAuthClient = new GitHubOAuthClient(webClient);
        ReflectionTestUtils.setField(gitHubOAuthClient, "clientId", "test-client-id");
        ReflectionTestUtils.setField(gitHubOAuthClient, "clientSecret", "test-client-secret");
    }

    // ── exchangeToken 정상 ──────────────────────────────────────

    @Test
    @DisplayName("exchangeToken - 정상 응답 시 access_token을 반환한다")
    void exchangeToken_success() {
        // given
        GitHubTokenResponse tokenResponse = new GitHubTokenResponse("gho_abc123", "bearer", "");
        stubPostChain(Mono.just(tokenResponse), GitHubTokenResponse.class);

        // when
        String result = gitHubOAuthClient.exchangeToken("auth-code");

        // then
        assertThat(result).isEqualTo("gho_abc123");
    }

    // ── exchangeToken 예외 ──────────────────────────────────────

    @Test
    @DisplayName("exchangeToken - access_token이 null이면 AUTH_SOCIAL_GITHUB_FAILED 예외가 발생한다")
    void exchangeToken_nullAccessToken_throwsGithubFailed() {
        // given
        GitHubTokenResponse tokenResponse = new GitHubTokenResponse(null, null, null);
        stubPostChain(Mono.just(tokenResponse), GitHubTokenResponse.class);

        // when & then
        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - WebClientResponseException 발생 시 AUTH_SOCIAL_GITHUB_FAILED로 변환된다")
    void exchangeToken_webClientException_throwsGithubFailed() {
        // given
        stubPostChain(
                Mono.error(makeWebClientException(HttpStatus.UNAUTHORIZED)),
                GitHubTokenResponse.class
        );

        // when & then
        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("expired-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    // ── fetchUserInfo 정상 ──────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo - 정상 응답 시 GitHubUserInfo를 반환한다")
    void fetchUserInfo_success() {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        stubGetChain(Mono.just(userInfo), GitHubUserInfo.class);

        // when
        GitHubUserInfo result = gitHubOAuthClient.fetchUserInfo("gho_abc123");

        // then
        assertThat(result.id()).isEqualTo("12345");
        assertThat(result.email()).isEqualTo("hayoung@test.com");
        assertThat(result.login()).isEqualTo("hayoung");
    }

    @Test
    @DisplayName("fetchUserInfo - name이 null이어도 정상적으로 반환한다 (GitHub 비공개 설정)")
    void fetchUserInfo_nullName_returnsUserInfo() {
        // given
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", null, null);
        stubGetChain(Mono.just(userInfo), GitHubUserInfo.class);

        // when
        GitHubUserInfo result = gitHubOAuthClient.fetchUserInfo("gho_abc123");

        // then
        assertThat(result.name()).isNull();
        assertThat(result.login()).isEqualTo("hayoung");
    }

    // ── fetchUserInfo 예외 ──────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo - 응답이 empty이면 AUTH_SOCIAL_GITHUB_FAILED 예외가 발생한다")
    void fetchUserInfo_emptyResponse_throwsGithubFailed() {
        // given
        stubGetChain(Mono.empty(), GitHubUserInfo.class);

        // when & then
        assertThatThrownBy(() -> gitHubOAuthClient.fetchUserInfo("gho_abc123"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("fetchUserInfo - WebClientResponseException 발생 시 AUTH_SOCIAL_GITHUB_FAILED로 변환된다")
    void fetchUserInfo_webClientException_throwsGithubFailed() {
        // given
        stubGetChain(
                Mono.error(makeWebClientException(HttpStatus.FORBIDDEN)),
                GitHubUserInfo.class
        );

        // when & then
        assertThatThrownBy(() -> gitHubOAuthClient.fetchUserInfo("bad-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────────

    private void stubPostChain(Mono mono, Class responseType) {
        given(webClient.post()).willReturn(postUriSpec);
        given(postUriSpec.uri(anyString())).willReturn(postBodySpec);
        given(postBodySpec.header(anyString(), anyString())).willReturn(postBodySpec);
        given(postBodySpec.bodyValue(any())).willReturn(postHeadersSpec);
        given(postHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(responseType)).willReturn(mono);
    }

    private void stubGetChain(Mono mono, Class responseType) {
        given(webClient.get()).willReturn(getUriSpec);
        given(getUriSpec.uri(anyString())).willReturn(getHeadersSpec);
        given(getHeadersSpec.header(anyString(), anyString())).willReturn(getHeadersSpec);
        given(getHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(responseType)).willReturn(mono);
    }

    private WebClientResponseException makeWebClientException(HttpStatus status) {
        return WebClientResponseException.create(
                status.value(), status.getReasonPhrase(),
                HttpHeaders.EMPTY, new byte[0], Charset.defaultCharset()
        );
    }
}
