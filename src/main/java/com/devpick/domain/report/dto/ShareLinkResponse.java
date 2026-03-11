package com.devpick.domain.report.dto;

import java.util.UUID;

public record ShareLinkResponse(
        UUID reportId,
        String shareToken
) {
}
