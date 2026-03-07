package com.devpick.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer Token — SecurityContext에 인증 정보가 설정된다")
    void doFilterInternal_validToken_setsAuthentication() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        given(jwtTokenProvider.isTokenValid("valid-token")).willReturn(true);
        given(jwtTokenProvider.extractUserId("valid-token")).willReturn(userId);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 Bearer Token — SecurityContext가 비어있고 필터 체인은 계속 진행된다")
    void doFilterInternal_invalidToken_doesNotSetAuthentication() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        given(jwtTokenProvider.isTokenValid("invalid-token")).willReturn(false);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더 없음 — SecurityContext가 비어있고 필터 체인은 계속 진행된다")
    void doFilterInternal_noHeader_doesNotSetAuthentication() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 접두사 없는 헤더 — 토큰 추출 안 되고 SecurityContext 비어있다")
    void doFilterInternal_noBearerPrefix_doesNotSetAuthentication() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic some-credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
