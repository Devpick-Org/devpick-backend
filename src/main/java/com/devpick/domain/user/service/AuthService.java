package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicateNickname(request.nickname());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createEmailUser(request.email(), encodedPassword, request.nickname());
        userRepository.save(user);

        return SignupResponse.from(user);
    }

    /**
     * 이메일/비밀번호 로그인 후 Access + Refresh Token 발급 (DP-181).
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND));

        if (!user.isEmailVerified()) {
            throw new DevpickException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new DevpickException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        return tokenService.issueTokenPair(user);
    }

    // ── private ──────────────────────────────────────────────

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DevpickException(ErrorCode.AUTH_DUPLICATE_EMAIL);
        }
    }

    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new DevpickException(ErrorCode.AUTH_DUPLICATE_NICKNAME);
        }
    }
}
