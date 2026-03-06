package com.devpick.domain.community.entity;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Answer Entity 단위 테스트")
class AnswerTest {

    @Test
    @DisplayName("빌더 기본값 - isAdopted=false")
    void builder_defaultIsAdoptedFalse() {
        // given
        User user = buildUser();
        Post post = buildPost(user);

        // when
        Answer answer = Answer.builder()
                .post(post)
                .user(user)
                .content("답변 내용입니다.")
                .build();

        // then
        assertThat(answer.getIsAdopted()).isFalse();
        assertThat(answer.getContent()).isEqualTo("답변 내용입니다.");
    }

    @Test
    @DisplayName("isAdopted=true 로 설정 가능하다")
    void builder_isAdoptedTrue() {
        // given
        User user = buildUser();
        Post post = buildPost(user);

        // when
        Answer answer = Answer.builder()
                .post(post)
                .user(user)
                .content("채택된 답변")
                .isAdopted(true)
                .build();

        // then
        assertThat(answer.getIsAdopted()).isTrue();
    }

    private User buildUser() {
        return User.builder()
                .email("test@devpick.com")
                .nickname("테스터")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
    }

    private Post buildPost(User user) {
        return Post.builder()
                .user(user)
                .title("질문")
                .content("내용")
                .level(Level.JUNIOR)
                .build();
    }
}
