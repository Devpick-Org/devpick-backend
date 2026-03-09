package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @deprecated SocialAuthService로 통합되었습니다.
 * 하위 호환성을 위해 임시 유지하며, AuthController가 SocialAuthService로 마이그레이션되면 제거합니다.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final SocialAuthService socialAuthService;

    public LoginResponse login(String code) {
        return socialAuthService.login("google", code);
    }
}
