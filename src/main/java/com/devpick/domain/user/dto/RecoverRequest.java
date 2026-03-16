package com.devpick.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoverRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
