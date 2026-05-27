package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostSummaryResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostAttachment;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
import com.devpick.domain.community.client.AiQuestionCleanupClient;
import com.devpick.domain.community.client.QuestionIndexClient;
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
import com.devpick.global.storage.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final Duration PUBLIC_LIST_CACHE_TTL = Duration.ofSeconds(30);

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
    private final FileStorageService fileStorageService;
    private final AiQuestionCleanupClient aiQuestionCleanupClient;
    private final QuestionIndexClient questionIndexClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
                .postType(request.postType())
                .title(request.title())
                .content(request.content())
                .level(request.level())
                .build();
        applyPostAttachments(post, request.attachmentUrls());
        Post savedPost = postRepository.save(post);

        historyRepository.save(History.builder()
                .user(user)
                .actionType("question_created")
                .post(savedPost)
                .build());
        pointService.earn(user, PointAction.QUESTION_WRITE);

        if (savedPost.getPostType() == PostType.TECH) {
            scheduleQuestionIndexing(savedPost);
        }

        return PostDetailResponse.of(savedPost, 0L);
    }

    @Transactional(readOnly = true)
    public PostListResponse getPosts(Pageable pageable, String query, PostType postType) {
        String cacheKey = publicListCacheKey(pageable, query, postType);
        PostListResponse cached = readPublicListCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Page<Post> page;
        if (StringUtils.hasText(query)) {
            String q = query.trim();
            Pageable sorted = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    pageable.getSort().isSorted()
                            ? pageable.getSort()
                            : Sort.by(Sort.Direction.DESC, "createdAt"));
            page = postType != null
                    ? postRepository.searchByPostTypeAndTitleOrContentContaining(postType, q, sorted)
                    : postRepository.searchByTitleOrContentContaining(q, sorted);
        } else {
            page = postType != null
                    ? postRepository.findAllByPostTypeOrderByCreatedAtDesc(postType, pageable)
                    : postRepository.findAllByOrderByCreatedAtDesc(pageable);
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
        Map<UUID, String> topAnswerPreviews = answerRepository.findFirstAnswerPreviewsByPostIds(postIds)
                .stream()
                .collect(Collectors.toMap(
                        AnswerRepository.AnswerPreviewRow::getPostId,
                        row -> PostSummaryResponse.truncateAnswerPreview(row.getContent()),
                        (first, second) -> first  // 가장 오래된 답변 유지
                ));
        if (!answerCountMap.isEmpty() && topAnswerPreviews.isEmpty()) {
            topAnswerPreviews = answerRepository.findByPostIdsOrderByCreatedAtAsc(postIds)
                    .stream()
                    .collect(Collectors.toMap(
                            a -> a.getPost().getId(),
                            a -> PostSummaryResponse.truncateAnswerPreview(a.getContent()),
                            (first, second) -> first
                    ));
        }
        Map<UUID, String> answerPreviewMap = topAnswerPreviews;

        List<PostSummaryResponse> posts = postList.stream()
                .map(post -> PostSummaryResponse.of(
                        post,
                        answerCountMap.getOrDefault(post.getId(), 0L),
                        answerPreviewMap.get(post.getId())
                ))
                .toList();

        PostListResponse response = new PostListResponse(posts, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
        writePublicListCache(cacheKey, response);
        return response;
    }

    private String publicListCacheKey(Pageable pageable, String query, PostType postType) {
        if (StringUtils.hasText(query) || postType != null) {
            return null;
        }
        return "posts:list:v1:page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize()
                + ":sort:" + pageable.getSort();
    }

    private PostListResponse readPublicListCache(String cacheKey) {
        if (cacheKey == null || redisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            var ops = redisTemplate.opsForValue();
            if (ops == null) {
                return null;
            }
            String json = ops.get(cacheKey);
            return json == null || json.isBlank() ? null : objectMapper.readValue(json, PostListResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writePublicListCache(String cacheKey, PostListResponse response) {
        if (cacheKey == null || redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            var ops = redisTemplate.opsForValue();
            if (ops != null) {
                ops.set(cacheKey, objectMapper.writeValueAsString(response), PUBLIC_LIST_CACHE_TTL);
            }
        } catch (JsonProcessingException ignored) {
            // 목록 캐시는 성능 최적화용이므로 직렬화 실패 시 DB 응답을 그대로 사용합니다.
        }
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(UUID postId) {
        Post post = postRepository.findByIdWithAttachments(postId)
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
        post.getAttachments().clear();
        applyPostAttachments(post, request.attachmentUrls());
        long answerCount = answerRepository.countByPost_Id(postId);
        return PostDetailResponse.of(post, answerCount);
    }

    private void applyPostAttachments(Post post, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            fileStorageService.validateUploadedAttachmentUrl(url);
            post.getAttachments().add(PostAttachment.builder()
                    .post(post)
                    .url(url)
                    .fileName(FileStorageService.extractFileNameFromUrl(url))
                    .type(FileStorageService.inferAttachmentTypeFromUrl(url))
                    .build());
        }
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION);
        }

        // 포인트 환불: QUESTION_WRITE
        pointService.refundLatestByAction(post.getUser(), PointAction.QUESTION_WRITE);

        // 댓글·답변 삭제 전에 history 제거 (comment_id / answer_id / post_id 참조, DP-324)
        historyRepository.deleteByAnswerPostId(postId);
        historyRepository.deleteByPostId(postId);

        commentRepository.deleteByPostId(postId);
        answerLikeRepository.deleteByPostId(postId);
        answerRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        aiAnswerRepository.deleteByPostId(postId);
        aiQuestionRepository.deleteByPostId(postId);
        postRepository.delete(post);

        if (post.getPostType() == PostType.TECH) {
            scheduleAiQuestionCleanup(postId);
        }
    }

    private void scheduleQuestionIndexing(Post post) {
        Runnable task = () -> questionIndexClient.indexQuestion(post);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * DB 커밋 후 AI 서버에 질문 문서 삭제를 요청한다. 트랜잭션이 없으면(단위 테스트 등) 즉시 스케줄한다.
     */
    private void scheduleAiQuestionCleanup(UUID postId) {
        Runnable task = () -> aiQuestionCleanupClient.notifyQuestionDeleted(postId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
