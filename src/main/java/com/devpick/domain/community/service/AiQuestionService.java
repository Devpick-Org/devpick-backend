package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiQuestionClient;
import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionService {

    private final AiQuestionClient aiQuestionClient;
    private final AiQuestionRepository aiQuestionRepository;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @Transactional
    public QuestionRefineResponse refine(UUID userId, QuestionRefineRequest request) {
        QuestionRefineResponse response = aiQuestionClient.refine(request);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(user ->
                    planLimitService.checkAndIncrementAiDaily(userId, user.getPlanType()));
        }

        // postId가 있으면 AiQuestion에 결과 저장 (AI 답변 생성 시 refined 데이터 활용)
        if (request.postId() != null) {
            postRepository.findById(request.postId()).ifPresent(post ->
                    saveRefinedQuestion(post, request.title(), response)
            );
        }

        return response;
    }

    private void saveRefinedQuestion(Post post, String originalTitle, QuestionRefineResponse response) {
        String suggestionsJson = null;
        try {
            suggestionsJson = objectMapper.writeValueAsString(response.suggestions());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize suggestions for post={}: {}", post.getId(), e.getMessage());
        }

        String finalSuggestionsJson = suggestionsJson;
        aiQuestionRepository.findByPost_Id(post.getId()).ifPresentOrElse(
                existing -> {
                    // 이미 존재하면 업데이트는 지원하지 않음 (첫 번째 refine 결과를 사용)
                    log.debug("AiQuestion already exists for postId={}, skipping save", post.getId());
                },
                () -> aiQuestionRepository.save(AiQuestion.builder()
                        .post(post)
                        .originalTitle(originalTitle)
                        .refinedTitle(response.refinedTitle())
                        .refinedContent(response.refinedContent())
                        .suggestions(finalSuggestionsJson)
                        .build())
        );
    }
}
