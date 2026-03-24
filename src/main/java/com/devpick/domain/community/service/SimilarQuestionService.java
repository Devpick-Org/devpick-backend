package com.devpick.domain.community.service;

import com.devpick.domain.community.client.SimilarQuestionClient;
import com.devpick.domain.community.dto.SimilarPostListResponse;
import com.devpick.domain.community.dto.SimilarPostResponse;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimilarQuestionService {

    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final SimilarQuestionClient similarQuestionClient;

    @Transactional(readOnly = true)
    public SimilarPostListResponse getSimilarPosts(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        String searchText = post.getTitle() + " " + post.getContent();
        List<UUID> similarIds = similarQuestionClient.searchSimilar(postId, searchText, 5);

        List<SimilarPostResponse> posts = similarIds.stream()
                .map(id -> postRepository.findById(id).orElse(null))
                .filter(p -> p != null)
                .map(p -> SimilarPostResponse.of(p, answerRepository.countByPost_Id(p.getId())))
                .toList();

        return new SimilarPostListResponse(posts);
    }
}
