package com.devpick.domain.community.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerUpdateRequest(
        @NotBlank String content
) {}
