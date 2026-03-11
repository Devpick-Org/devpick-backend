package com.devpick.domain.community.dto;

import com.devpick.domain.user.entity.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionRefineRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String content,
        @NotNull Level level
) {}
