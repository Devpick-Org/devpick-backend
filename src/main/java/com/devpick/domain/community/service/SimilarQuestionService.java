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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        if (similarIds.isEmpty()) {
            return new SimilarPostListResponse(List.of());
        }

        Map<UUID, Post> postMap = postRepository.findAllById(similarIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        Map<UUID, Long> countMap = answerRepository.countByPostIds(similarIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        List<SimilarPostResponse> posts = similarIds.stream()
                .filter(postMap::containsKey)
                .map(id -> SimilarPostResponse.of(postMap.get(id), countMap.getOrDefault(id, 0L)))
                .toList();

        return new SimilarPostListResponse(posts);
    }
}
