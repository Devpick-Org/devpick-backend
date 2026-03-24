package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiAnswerClient;
import com.devpick.domain.community.dto.AiAnswerResponse;
import com.devpick.domain.community.entity.AiAnswer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
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
    private final PostRepository postRepository;
    private final AiAnswerClient aiAnswerClient;

    @Transactional
    public AiAnswerResponse generateOrGetAnswer(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        return aiAnswerRepository.findByPost_Id(postId)
                .map(AiAnswerResponse::of)
                .orElseGet(() -> {
                    String content = aiAnswerClient.generateAnswer(post);
                    AiAnswer saved = aiAnswerRepository.save(AiAnswer.builder()
                            .post(post)
                            .content(content)
                            .build());
                    return AiAnswerResponse.of(saved);
                });
    }
}
