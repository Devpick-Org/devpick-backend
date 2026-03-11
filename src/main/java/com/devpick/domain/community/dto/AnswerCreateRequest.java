package com.devpick.domain.community.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerCreateRequest(
        @NotBlank String content
) {}
