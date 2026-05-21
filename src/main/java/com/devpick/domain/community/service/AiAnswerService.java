package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiAnswerClient;
import com.devpick.domain.community.dto.AiAnswerResponse;
import com.devpick.domain.community.entity.AiAnswer;
import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnswerService {

    private final AiAnswerRepository aiAnswerRepository;
    private final AiQuestionRepository aiQuestionRepository;
    private final PostRepository postRepository;
    private final AiAnswerClient aiAnswerClient;
    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @Transactional
    public AiAnswerResponse generateOrGetAnswer(UUID userId, UUID postId) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user ->
                    planLimitService.checkAndIncrementAiDaily(userId, user.getPlanType()));
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (post.getPostType() == PostType.CAREER) {
            throw new DevpickException(ErrorCode.COMMUNITY_AI_NOT_SUPPORTED);
        }

        return aiAnswerRepository.findByPost_Id(postId)
                .map(AiAnswerResponse::of)
                .orElseGet(() -> {
                    AiQuestion aiQuestion = aiQuestionRepository.findByPost_Id(postId).orElse(null);
                    AiAnswerClient.AiAnswerFastApiResponse aiResponse =
                            aiAnswerClient.generateAnswer(post, aiQuestion);
                    AiAnswer saved = aiAnswerRepository.save(AiAnswer.builder()
                            .post(post)
                            .content(aiResponse.answerContent())
                            .keyPoints(aiResponse.keyPoints())
                            .suggestedTags(aiResponse.suggestedTags())
                            .confidence(aiResponse.confidence())
                            .build());
                    return AiAnswerResponse.of(saved);
                });
    }
}
