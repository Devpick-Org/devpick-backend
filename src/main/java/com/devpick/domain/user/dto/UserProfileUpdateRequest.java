package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserProfileUpdateRequest(
        @Size(min = 2, max = 20) String nickname,
        String profileImage,
        Job job,
        Level level,
        List<String> tags
) {
}
