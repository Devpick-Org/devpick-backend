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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class GitHubOAuthClientTest {

    private GitHubOAuthClient gitHubOAuthClient;

    private WebClient webClient;
    private WebClient.RequestBodyUriSpec postSpec;
    private WebClient.RequestBodySpec bodySpec;
    private WebClient.RequestHeadersSpec postHeadersSpec;
    private WebClient.RequestHeadersUriSpec getSpec;
    private WebClient.RequestHeadersSpec getHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() throws Exception {
        webClient = mock(WebClient.class);
        postSpec = mock(WebClient.RequestBodyUriSpec.class);
        bodySpec = mock(WebClient.RequestBodySpec.class);
        postHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        getSpec = mock(WebClient.RequestHeadersUriSpec.class);
        getHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        gitHubOAuthClient = new GitHubOAuthClient(webClient);
        inject("clientId", "test-client-id");
        inject("clientSecret", "test-client-secret");
        inject("tokenUrl", "https://github.com/login/oauth/access_token");
        inject("userUrl", "https://api.github.com/user");
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field f = GitHubOAuthClient.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(gitHubOAuthClient, value);
    }

    // ── getProviderName ──────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName - \"github\"을 반환한다")
    void getProviderName_returnsGithub() {
        assertThat(gitHubOAuthClient.getProviderName()).isEqualTo("github");
    }

    // ── exchangeToken ──────────────────────────────────────────────

    @Test
    @DisplayName("exchangeToken - 정상 응답 시 access_token을 반환한다")
    void exchangeToken_success() {
        GitHubTokenResponse tokenResponse = new GitHubTokenResponse("github-token", "bearer", "repo", null, null);
        stubPost(Mono.just(tokenResponse), GitHubTokenResponse.class);

        String result = gitHubOAuthClient.exchangeToken("auth-code");

        assertThat(result).isEqualTo("github-token");
    }

    @Test
    @DisplayName("exchangeToken - block()이 null을 반환하면 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void exchangeToken_blockReturnsNull_throws() {
        stubPost(Mono.empty(), GitHubTokenResponse.class);

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - access_token이 null이면 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void exchangeToken_nullAccessToken_throws() {
        stubPost(Mono.just(new GitHubTokenResponse(null, null, null, null, null)), GitHubTokenResponse.class);

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - error=bad_verification_code 이면 AUTH_OAUTH_CODE_EXPIRED 예외 발생")
    void exchangeToken_badVerificationCode_throws() {
        stubPost(Mono.just(new GitHubTokenResponse(null, null, null, "bad_verification_code", "The code passed is incorrect or expired.")), GitHubTokenResponse.class);

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("expired-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_CODE_EXPIRED);
    }

    @Test
    @DisplayName("exchangeToken - error=access_denied 이면 AUTH_OAUTH_ACCESS_DENIED 예외 발생")
    void exchangeToken_accessDenied_throws() {
        stubPost(Mono.just(new GitHubTokenResponse(null, null, null, "access_denied", "The user has denied your application access.")), GitHubTokenResponse.class);

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("denied-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_ACCESS_DENIED);
    }

    @Test
    @DisplayName("exchangeToken - 알 수 없는 error 필드이면 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void exchangeToken_unknownError_throws() {
        stubPost(Mono.just(new GitHubTokenResponse(null, null, null, "unknown_error", "Unknown error occurred.")), GitHubTokenResponse.class);

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - WebClientResponseException 발생 시 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void exchangeToken_webClientException_throws() {
        stubPostThrows(WebClientResponseException.create(502, "Bad Gateway", null, null, null));

        assertThatThrownBy(() -> gitHubOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    // ── fetchUserInfo ──────────────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo - 정상 응답 시 사용자 정보를 반환한다")
    void fetchUserInfo_success() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        stubGet(Mono.just(userInfo), GitHubUserInfo.class);

        GitHubUserInfo result = (GitHubUserInfo) gitHubOAuthClient.fetchUserInfo("access-token");

        assertThat(result.id()).isEqualTo("12345");
        assertThat(result.email()).isEqualTo("hayoung@test.com");
    }

    @Test
    @DisplayName("fetchUserInfo - OAuthUserInfo 인터페이스 메서드가 올바르게 동작한다")
    void fetchUserInfo_oauthUserInfoInterface_works() {
        GitHubUserInfo userInfo = new GitHubUserInfo("12345", "hayoung", "hayoung@test.com", "하영", null);
        stubGet(Mono.just(userInfo), GitHubUserInfo.class);

        var result = gitHubOAuthClient.fetchUserInfo("access-token");

        assertThat(result.getProviderId()).isEqualTo("12345");
        assertThat(result.getEmail()).isEqualTo("hayoung@test.com");
        assertThat(result.getNicknamePrefix()).isEqualTo("hayoung"); // GitHub: login
    }

    @Test
    @DisplayName("fetchUserInfo - 응답이 null(Mono.empty)이면 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void fetchUserInfo_nullResponse_throws() {
        stubGet(Mono.empty(), GitHubUserInfo.class);

        assertThatThrownBy(() -> gitHubOAuthClient.fetchUserInfo("access-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    @Test
    @DisplayName("fetchUserInfo - WebClientResponseException 발생 시 AUTH_SOCIAL_GITHUB_FAILED 예외 발생")
    void fetchUserInfo_webClientException_throws() {
        stubGetThrows(WebClientResponseException.create(401, "Unauthorized", null, null, null));

        assertThatThrownBy(() -> gitHubOAuthClient.fetchUserInfo("invalid-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
    }

    // ── stub helpers ──────────────────────────────────────────────

    private void stubPost(Mono mono, Class responseType) {
        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(responseType)).thenReturn(mono);
    }

    private void stubPostThrows(RuntimeException ex) {
        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(GitHubTokenResponse.class)).thenThrow(ex);
    }

    private void stubGet(Mono mono, Class responseType) {
        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.header(anyString(), anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(responseType)).thenReturn(mono);
    }

    private void stubGetThrows(RuntimeException ex) {
        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.header(anyString(), anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(GitHubUserInfo.class)).thenThrow(ex);
    }
}
