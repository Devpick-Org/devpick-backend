package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostSummaryResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;

    @Transactional
    public PostDetailResponse createPost(UUID userId, PostCreateRequest request) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Post post = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .level(request.level())
                .build();
        Post savedPost = postRepository.save(post);

        historyRepository.save(History.builder()
                .user(user)
                .actionType("question_created")
                .post(savedPost)
                .build());

        return PostDetailResponse.of(savedPost, 0L);
    }

    @Transactional(readOnly = true)
    public PostListResponse getPosts(Pageable pageable) {
        Page<Post> page = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        List<PostSummaryResponse> posts = page.getContent().stream()
                .map(PostSummaryResponse::of)
                .toList();
        return new PostListResponse(posts, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        long answerCount = answerRepository.countByPost_Id(postId);
        return PostDetailResponse.of(post, answerCount);
    }

    @Transactional
    public PostDetailResponse updatePost(UUID userId, UUID postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION);
        }

        post.update(request.title(), request.content(), request.level());
        long answerCount = answerRepository.countByPost_Id(postId);
        return PostDetailResponse.of(post, answerCount);
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION);
        }

        postRepository.delete(post);
    }
}
