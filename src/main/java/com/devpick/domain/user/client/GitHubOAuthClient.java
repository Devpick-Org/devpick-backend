package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GitHubTokenResponse;
import com.devpick.domain.user.dto.GitHubUserInfo;
import com.devpick.domain.user.dto.OAuthUserInfo;
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
public class GitHubOAuthClient implements OAuthProviderClient {

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

    @Override
    public String getProviderName() {
        return "github";
    }

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
     *
     * GitHub 스펙: HTTP 200 OK로 응답하되 body에 error 필드가 있으면 실패를 의미한다.
     * - bad_verification_code : 코드 만료/미일치 → AUTH_OAUTH_CODE_EXPIRED
     * - access_denied         : 사용자 취소     → AUTH_OAUTH_ACCESS_DENIED
     * - 기타 error            : 처리 실패       → AUTH_SOCIAL_GITHUB_FAILED
     */
    @Override
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

            if (response == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
            }

            // GitHub는 200 OK에 error 필드를 담아 실패를 알린다
            if (response.error() != null) {
                throw new DevpickException(resolveGitHubTokenError(response.error()));
            }

            if (response.accessToken() == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
            }

            return response.accessToken();
        } catch (DevpickException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
        }
    }

    /**
     * GitHub Access Token → GitHub 사용자 정보 조회 (DP-183).
     */
    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
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
        } catch (DevpickException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
        }
    }

    /**
     * GitHub body error 값에 따른 세분화 매핑.
     */
    private ErrorCode resolveGitHubTokenError(String error) {
        return switch (error) {
            case "bad_verification_code" -> ErrorCode.AUTH_OAUTH_CODE_EXPIRED;
            case "access_denied"         -> ErrorCode.AUTH_OAUTH_ACCESS_DENIED;
            default                      -> ErrorCode.AUTH_SOCIAL_GITHUB_FAILED;
        };
    }
}
