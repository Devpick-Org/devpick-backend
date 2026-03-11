package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GitHubTokenResponse;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitHubOAuthClient {

    private final WebClient webClient;

    @Value("${oauth.github.client-id}")
    private String clientId;

    @Value("${oauth.github.client-secret}")
    private String clientSecret;

    // 확장 포인트 (DP-183): 테스트 주입을 위해 @Value로 노출. 기본값은 실제 GitHub URL.
    @Value("${oauth.github.token-url:https://github.com/login/oauth/access_token}")
    private String tokenUrl;

    @Value("${oauth.github.user-url:https://api.github.com/user}")
    private String userUrl;

    @Value("${oauth.github.authorize-url:https://github.com/login/oauth/authorize}")
    private String authorizeUrl;

    @Value("${oauth.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * GitHub OAuth 인가 URL 생성 (DP-284).
     * state 파라미터로 CSRF 방지.
     */
    public String getAuthorizationUrl(String state) {
        return authorizeUrl
                + "?client_id=" + clientId
                + "&redirect_uri=" + frontendBaseUrl + "/auth/github/callback"
                + "&scope=user:email"
                + "&state=" + state;
    }

    /**
     * GitHub 인가 코드 → GitHub Access Token 교환 (DP-183).
     */
    public String exchangeToken(String code) {
        try {
            GitHubTokenResponse response = webClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code
                    ))
                    .retrieve()
                    .bodyToMono(GitHubTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
            }
            return response.accessToken();
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
        }
    }

    /**
     * GitHub Access Token → GitHub 사용자 정보 조회 (DP-183).
     */
    public GitHubUserInfo fetchUserInfo(String accessToken) {
        try {
            GitHubUserInfo userInfo = webClient.get()
                    .uri(userUrl)
                    .header(HttpHeaders.AUTHORIZATION, "token " + accessToken)
                    .retrieve()
                    .bodyToMono(GitHubUserInfo.class)
                    .block();

            if (userInfo == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
            }
            return userInfo;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
        }
    }
}
