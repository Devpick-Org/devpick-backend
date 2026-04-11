package com.devpick.domain.community.dto;

import com.devpick.domain.user.entity.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record QuestionRefineRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String content,
        @NotNull Level level,
        /** 기존 게시글에 대한 refine 결과를 저장할 때 postId를 함께 전달한다 (선택). */
        UUID postId
) {}
