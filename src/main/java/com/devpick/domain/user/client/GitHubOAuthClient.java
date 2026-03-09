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

    /**
     * GitHub 인가 코드 → GitHub Access Token 교환 (DP-183).
     *
     * <p>GitHub는 토큰 교환 실패 시에도 HTTP 200을 반환하고 body에 error 필드를 담는다.
     * 따라서 HTTP 상태코드가 아닌 응답 body의 error 값으로 실패를 판별해야 한다.
     *
     * <p>주요 error 값:
     * <ul>
     *   <li>{@code bad_verification_code} - 코드 만료 또는 이미 사용됨 (10분 이내 1회만 사용 가능)</li>
     *   <li>{@code access_denied}         - 사용자가 OAuth 동의 화면에서 취소</li>
     * </ul>
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

            if (response == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED);
            }

            // GitHub는 에러 시에도 HTTP 200 반환 → body error 필드로 판별
            if (response.error() != null) {
                throw new DevpickException(resolveGitHubTokenError(response.error()));
            }

            if (response.accessToken() == null) {
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

    /**
     * GitHub error 값 → DevpickException ErrorCode 매핑.
     *
     * @see <a href="https://docs.github.com/en/apps/oauth-apps/maintaining-oauth-apps/troubleshooting-oauth-app-access-token-request-errors">GitHub OAuth error 문서</a>
     */
    private ErrorCode resolveGitHubTokenError(String error) {
        return switch (error) {
            case "bad_verification_code" -> ErrorCode.AUTH_OAUTH_CODE_EXPIRED;
            case "access_denied"         -> ErrorCode.AUTH_OAUTH_ACCESS_DENIED;
            default                      -> ErrorCode.AUTH_SOCIAL_GITHUB_FAILED;
        };
    }
}
