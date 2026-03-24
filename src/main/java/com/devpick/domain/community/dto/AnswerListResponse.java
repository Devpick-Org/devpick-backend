package com.devpick.domain.community.dto;

import java.util.List;

public record AnswerListResponse(List<AnswerWithCommentsResponse> answers) {
}
