package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostSummaryResponseTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
    }

    // ── truncateAnswerPreview ──────────────────────────────────────────────

    @Test
    @DisplayName("truncateAnswerPreview — null 입력 시 null 반환")
    void truncateAnswerPreview_null_returnsNull() {
        assertThat(PostSummaryResponse.truncateAnswerPreview(null)).isNull();
    }

    @Test
    @DisplayName("truncateAnswerPreview — 100자 이하 입력 시 원문 그대로 반환")
    void truncateAnswerPreview_shortContent_returnsUnchanged() {
        String content = "Short answer";
        assertThat(PostSummaryResponse.truncateAnswerPreview(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("truncateAnswerPreview — 정확히 100자 입력 시 원문 그대로 반환")
    void truncateAnswerPreview_exactlyHundredChars_returnsUnchanged() {
        String content = "a".repeat(100);
        assertThat(PostSummaryResponse.truncateAnswerPreview(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("truncateAnswerPreview — 100자 초과 시 100자 + '...' 반환")
    void truncateAnswerPreview_longContent_returnsTruncated() {
        String content = "a".repeat(150);
        String result = PostSummaryResponse.truncateAnswerPreview(content);
        assertThat(result).endsWith("...");
        assertThat(result).isEqualTo("a".repeat(100) + "...");
    }

    // ── of() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("of — 본문 150자 이하 시 contentPreview 그대로")
    void of_shortContent_preservesContentPreview() {
        Post post = buildPost("Short content");

        PostSummaryResponse response = PostSummaryResponse.of(post, 0L, null);

        assertThat(response.contentPreview()).isEqualTo("Short content");
    }

    @Test
    @DisplayName("of — 본문 150자 초과 시 150자 + '...' 로 잘림")
    void of_longContent_truncatesContentPreview() {
        Post post = buildPost("a".repeat(200));

        PostSummaryResponse response = PostSummaryResponse.of(post, 5L, "top answer");

        assertThat(response.contentPreview()).isEqualTo("a".repeat(150) + "...");
        assertThat(response.answerCount()).isEqualTo(5L);
        assertThat(response.topAnswerPreview()).isEqualTo("top answer");
    }

    @Test
    @DisplayName("of — 본문 null 시 contentPreview null")
    void of_nullContent_nullContentPreview() {
        Post post = buildPost(null);

        PostSummaryResponse response = PostSummaryResponse.of(post, 0L, null);

        assertThat(response.contentPreview()).isNull();
    }

    @Test
    @DisplayName("of — createdAt null 시 Instant null")
    void of_nullCreatedAt_nullInstant() {
        Post post = buildPost("content");
        // @PrePersist 미실행 → createdAt = null

        PostSummaryResponse response = PostSummaryResponse.of(post, 0L, null);

        assertThat(response.createdAt()).isNull();
    }

    @Test
    @DisplayName("of — createdAt 있으면 Instant로 변환")
    void of_nonNullCreatedAt_returnsInstant() {
        Post post = buildPost("content");
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));

        PostSummaryResponse response = PostSummaryResponse.of(post, 2L, null);

        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("of — 모든 필드가 올바르게 매핑됨")
    void of_allFieldsMappedCorrectly() {
        Post post = buildPost("content");
        UUID postId = UUID.randomUUID();
        ReflectionTestUtils.setField(post, "id", postId);

        PostSummaryResponse response = PostSummaryResponse.of(post, 3L, "answer preview");

        assertThat(response.id()).isEqualTo(postId);
        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.level()).isEqualTo(Level.JUNIOR);
        assertThat(response.authorNickname()).isEqualTo("tester");
        assertThat(response.authorJob()).isEqualTo(Job.BACKEND);
        assertThat(response.answerCount()).isEqualTo(3L);
        assertThat(response.topAnswerPreview()).isEqualTo("answer preview");
    }

    private Post buildPost(String content) {
        return Post.builder()
                .user(user)
                .postType(PostType.TECH)
                .title("Test Post")
                .content(content)
                .level(Level.JUNIOR)
                .build();
    }
}
