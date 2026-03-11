package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiQuestionClient;
import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiQuestionService {

    private final AiQuestionClient aiQuestionClient;

    public QuestionRefineResponse refine(QuestionRefineRequest request) {
        return aiQuestionClient.refine(request);
    }
}
