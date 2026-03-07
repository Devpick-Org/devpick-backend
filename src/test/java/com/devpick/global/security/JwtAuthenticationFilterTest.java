package com.devpick.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtProvider jwtProvider;
    private JwtAuthenticationFilter filter;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder()
                .encodeToString("test-jwt-secret-key-123456789012".getBytes());
        jwtProvider = new JwtProvider(secret, 3600000L);
        filter = new JwtAuthenticationFilter(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 - SecurityContext에 인증 정보 설정")
    void validToken_setsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.createAccessToken(userId, "test@devpick.kr");
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        UserPrincipal principal = (UserPrincipal)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("test@devpick.kr");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더 없음 - SecurityContext 비어있고 필터 체인 통과")
    void noToken_doesNotSetAuthentication() throws Exception {
        given(request.getHeader("Authorization")).willReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("잘못된 토큰 - SecurityContext 비어있고 필터 체인 통과")
    void invalidToken_doesNotSetAuthentication() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer invalid.token");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 접두어 없는 헤더 - SecurityContext 비어있고 필터 체인 통과")
    void tokenWithoutBearer_doesNotSetAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.createAccessToken(userId, "test@devpick.kr");
        given(request.getHeader("Authorization")).willReturn(token);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
