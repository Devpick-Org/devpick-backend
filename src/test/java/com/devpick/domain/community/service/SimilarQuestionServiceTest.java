package com.devpick.domain.community.service;

import com.devpick.domain.community.client.SimilarQuestionClient;
import com.devpick.domain.community.dto.SimilarPostListResponse;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.user.entity.Level;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SimilarQuestionServiceTest {

    @InjectMocks
    private SimilarQuestionService similarQuestionService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private SimilarQuestionClient similarQuestionClient;

    private UUID postId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = Post.builder()
                .title("Spring 질문")
                .content("내용입니다")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Test
    @DisplayName("게시글이 없으면 COMMUNITY_POST_NOT_FOUND 예외가 발생한다")
    void getSimilarPosts_postNotFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> similarQuestionService.getSimilarPosts(postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    @Test
    @DisplayName("유사 질문 조회 성공 시 FastAPI 결과를 기반으로 게시글 목록을 반환한다")
    void getSimilarPosts_success_returnsList() {
        UUID similarId = UUID.randomUUID();
        Post similarPost = Post.builder()
                .title("유사 Spring 질문")
                .content("유사 내용")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(similarPost, "id", similarId);

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(similarQuestionClient.searchSimilar(postId, "Spring 질문 내용입니다", 5))
                .willReturn(List.of(similarId));
        given(postRepository.findById(similarId)).willReturn(Optional.of(similarPost));
        given(answerRepository.countByPost_Id(similarId)).willReturn(2L);

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).hasSize(1);
        assertThat(result.posts().get(0).id()).isEqualTo(similarId);
        assertThat(result.posts().get(0).title()).isEqualTo("유사 Spring 질문");
        assertThat(result.posts().get(0).answerCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("FastAPI가 반환한 ID가 DB에 없으면 결과에서 제외된다")
    void getSimilarPosts_missingPost_filtered() {
        UUID missingId = UUID.randomUUID();

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(similarQuestionClient.searchSimilar(postId, "Spring 질문 내용입니다", 5))
                .willReturn(List.of(missingId));
        given(postRepository.findById(missingId)).willReturn(Optional.empty());

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("FastAPI가 빈 목록을 반환하면 빈 배열을 반환한다")
    void getSimilarPosts_empty_returnsEmptyList() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(similarQuestionClient.searchSimilar(postId, "Spring 질문 내용입니다", 5))
                .willReturn(List.of());

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).isEmpty();
    }
}
