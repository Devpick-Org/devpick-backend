package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.OAuthUserInfo;

/**
 * OAuth 제공자별 클라이언트 구현체를 추상화한 전략 인터페이스.
 *
 * Strategy Pattern에서 ConcreteStrategy에 해당한다.
 * SocialAuthService가 List<OAuthProviderClient>를 주입받아 provider 이름으로 라우팅한다.
 */
public interface OAuthProviderClient {

    /**
     * OAuth 인가 URL 생성.
     * @param state CSRF 방지용 state 값
     * @return OAuth provider 인가 URL
     */
    String getAuthorizationUrl(String state);

    /**
     * 인가 코드 → Access Token 교환.
     * @param code OAuth 콜백으로 받은 인가 코드
     * @return 확인된 provider access token
     */
    String exchangeToken(String code);

    /**
     * Access Token → 사용자 정보 조회.
     * @param accessToken provider에서 받은 access token
     * @return OAuthUserInfo 구현체
     */
    OAuthUserInfo fetchUserInfo(String accessToken);

    /**
     * 제공자 이름 (ex. "github", "google").
     * SocialAuthService가 라우팅 키로 사용한다.
     */
    String getProviderName();
}
