package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.user.entity.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
        @NotNull PostType postType,
        @NotBlank @Size(max = 500) String title,
        @NotBlank String content,
        @NotNull Level level,
        @Size(max = 10) List<String> attachmentUrls
) {
    public PostCreateRequest {
        attachmentUrls = attachmentUrls == null ? List.of() : List.copyOf(attachmentUrls);
    }
}
