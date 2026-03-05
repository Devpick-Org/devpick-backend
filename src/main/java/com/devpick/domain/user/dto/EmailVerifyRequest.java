package com.devpick.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일 인증 코드 검증 요청 DTO.
 *
 * @param email 인증할 이메일
 * @param code  6자리 인증 코드
 */
public record EmailVerifyRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자입니다.")
        String code
) {
}
