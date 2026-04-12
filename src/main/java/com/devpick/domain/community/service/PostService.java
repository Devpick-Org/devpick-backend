package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostSummaryResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
import com.devpick.domain.community.repository.PostLikeRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final PointService pointService;
    private final PostLikeRepository postLikeRepository;
    private final AiAnswerRepository aiAnswerRepository;
    private final AiQuestionRepository aiQuestionRepository;
    private final CommentRepository commentRepository;
    private final AnswerLikeRepository answerLikeRepository;

    @Transactional
    public PostDetailResponse createPost(UUID userId, PostCreateRequest request) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        // 10초 이내 동일 제목 중복 제출 방지
        if (postRepository.existsByUser_IdAndTitleAndCreatedAtAfter(userId, request.title(), LocalDateTime.now().minusSeconds(10))) {
            throw new DevpickException(ErrorCode.COMMUNITY_DUPLICATE_POST);
        }

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
        pointService.earn(user, PointAction.QUESTION_WRITE);

        return PostDetailResponse.of(savedPost, 0L);
    }

    @Transactional(readOnly = true)
    public PostListResponse getPosts(Pageable pageable, String query) {
        Page<Post> page;
        if (StringUtils.hasText(query)) {
            String q = query.trim();
            Pageable sorted = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    pageable.getSort().isSorted()
                            ? pageable.getSort()
                            : Sort.by(Sort.Direction.DESC, "createdAt"));
            page = postRepository.searchByTitleOrContentContaining(q, sorted);
        } else {
            page = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<Post> postList = page.getContent();
        if (postList.isEmpty()) {
            return new PostListResponse(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        List<UUID> postIds = postList.stream().map(Post::getId).toList();

        // 답변 수 배치 조회
        Map<UUID, Long> answerCountMap = answerRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        // 첫 번째 답변 미리보기 배치 조회 (postId → truncated content)
        Map<UUID, String> topAnswerPreviews = answerRepository.findByPostIdsOrderByCreatedAtAsc(postIds)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getPost().getId(),
                        a -> PostSummaryResponse.truncateAnswerPreview(a.getContent()),
                        (first, second) -> first  // 가장 오래된 답변 유지
                ));

        List<PostSummaryResponse> posts = postList.stream()
                .map(post -> PostSummaryResponse.of(
                        post,
                        answerCountMap.getOrDefault(post.getId(), 0L),
                        topAnswerPreviews.get(post.getId())
                ))
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

        // 자식 레코드 순서대로 삭제 (FK 제약조건 준수)
        commentRepository.deleteByPostId(postId);
        answerLikeRepository.deleteByPostId(postId);
        historyRepository.deleteByAnswerPostId(postId);
        answerRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        aiAnswerRepository.deleteByPostId(postId);
        aiQuestionRepository.deleteByPostId(postId);
        historyRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }
}
