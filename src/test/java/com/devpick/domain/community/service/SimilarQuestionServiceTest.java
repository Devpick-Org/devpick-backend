package com.devpick.domain.community.service;

import com.devpick.domain.community.client.SimilarQuestionClient;
import com.devpick.domain.community.dto.SimilarPostListResponse;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
                .postType(PostType.TECH)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Test
    @DisplayName("CAREER 유형 게시글은 COMMUNITY_AI_NOT_SUPPORTED 예외가 발생한다")
    void getSimilarPosts_careerPost_throwsException() {
        Post careerPost = Post.builder()
                .title("커리어 질문")
                .content("내용입니다")
                .postType(PostType.CAREER)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(careerPost, "id", postId);

        given(postRepository.findById(postId)).willReturn(Optional.of(careerPost));

        assertThatThrownBy(() -> similarQuestionService.getSimilarPosts(postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_AI_NOT_SUPPORTED));
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
        given(similarQuestionClient.searchSimilar(eq(postId), any(), anyString(), anyInt()))
                .willReturn(List.of(similarId));
        given(postRepository.findAllById(List.of(similarId))).willReturn(List.of(similarPost));
        given(answerRepository.countByPostIds(List.of(similarId)))
                .willReturn(List.<Object[]>of(new Object[]{similarId, 2L}));

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
        given(similarQuestionClient.searchSimilar(eq(postId), any(), anyString(), anyInt()))
                .willReturn(List.of(missingId));
        given(postRepository.findAllById(List.of(missingId))).willReturn(List.of());
        given(answerRepository.countByPostIds(List.of(missingId))).willReturn(List.of());

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("FastAPI가 빈 목록을 반환하면 배치 쿼리 없이 빈 배열을 반환한다")
    void getSimilarPosts_empty_returnsEmptyList() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(similarQuestionClient.searchSimilar(eq(postId), any(), anyString(), anyInt()))
                .willReturn(List.of());

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).isEmpty();
        verify(postRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
        verify(answerRepository, never()).countByPostIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("유사 질문 결과가 AI 서버 응답 순서대로 반환된다")
    void getSimilarPosts_preservesOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Post p1 = buildPost(id1, "질문1");
        Post p2 = buildPost(id2, "질문2");
        Post p3 = buildPost(id3, "질문3");

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(similarQuestionClient.searchSimilar(eq(postId), any(), anyString(), anyInt()))
                .willReturn(List.of(id1, id2, id3));
        // findAllById may return in any order
        given(postRepository.findAllById(List.of(id1, id2, id3))).willReturn(List.of(p3, p1, p2));
        given(answerRepository.countByPostIds(List.of(id1, id2, id3))).willReturn(List.of());

        SimilarPostListResponse result = similarQuestionService.getSimilarPosts(postId);

        assertThat(result.posts()).hasSize(3);
        assertThat(result.posts().get(0).id()).isEqualTo(id1);
        assertThat(result.posts().get(1).id()).isEqualTo(id2);
        assertThat(result.posts().get(2).id()).isEqualTo(id3);
    }

    private Post buildPost(UUID id, String title) {
        Post p = Post.builder().title(title).content("내용").postType(PostType.TECH).level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
