package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.RecoverRequest;
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

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailVerificationRedisService emailVerificationRedisService;

    /**
     * 이메일 회원가입 (DP-177 수정 — 이메일 인증 후 가입 흐름).
     *
     * 스텝:
     *  1. Redis에서 이메일 인증 완료 여부 확인 (email:verified:{email})
     *  2. 이메일 중복/탈퇴 계정 처리
     *  3. 닉네임 중복 확인 (활성 계정 기준)
     *  4. User 생성 (is_email_verified = true)
     *  5. Redis 인증완료 플래그 삭제
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!emailVerificationRedisService.isVerified(request.email())) {
            throw new DevpickException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED_FOR_SIGNUP);
        }

        handleExistingEmailOnSignup(request.email());
        validateDuplicateNickname(request.nickname());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createVerifiedEmailUser(request.email(), encodedPassword, request.nickname());
        userRepository.save(user);

        emailVerificationRedisService.deleteVerified(request.email());

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

        if (!user.getIsActive()) {
            if (user.isRecoverable()) {
                throw new DevpickException(ErrorCode.AUTH_ACCOUNT_RECOVERABLE,
                        Map.of("deletedAt", user.getDeletedAt()));
            }
            throw new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND);
        }

        return tokenService.issueTokenPair(user);
    }

    /**
     * 탈퇴 후 7일 이내 계정 복구 + 토큰 발급.
     */
    @Transactional
    public LoginResponse recover(RecoverRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND));

        if (!user.isRecoverable()) {
            throw new DevpickException(ErrorCode.AUTH_ACCOUNT_DELETED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new DevpickException(ErrorCode.AUTH_INVALID_PASSWORD);
        }

        user.reactivate();
        return tokenService.issueTokenPair(user);
    }

    // ── private ────────────────────────────────────────────────────────

    /**
     * 가입 요청 이메일에 기존 계정이 있을 경우 처리:
     *  - 활성 계정 → AUTH_DUPLICATE_EMAIL
     *  - 탈퇴 후 7일 이내 → AUTH_ACCOUNT_RECOVERABLE
     *  - 탈퇴 후 7일 경과 → 익명화 후 신규 가입 진행
     */
    private void handleExistingEmailOnSignup(String email) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getIsActive()) {
                throw new DevpickException(ErrorCode.AUTH_DUPLICATE_EMAIL);
            }
            if (existing.isRecoverable()) {
                throw new DevpickException(ErrorCode.AUTH_ACCOUNT_RECOVERABLE,
                        Map.of("deletedAt", existing.getDeletedAt()));
            }
            existing.anonymize();
        });
    }

    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNicknameAndIsActiveTrue(nickname)) {
            throw new DevpickException(ErrorCode.AUTH_DUPLICATE_NICKNAME);
        }
    }
}
