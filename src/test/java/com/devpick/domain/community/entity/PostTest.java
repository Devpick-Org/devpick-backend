package com.devpick.domain.community.entity;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Post Entity 단위 테스트")
class PostTest {

    @Test
    @DisplayName("빌더로 Post 를 생성할 수 있다")
    void builder_createsPost() {
        // given
        User user = User.builder()
                .email("hay@devpick.com")
                .nickname("하영")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();

        // when
        Post post = Post.builder()
                .user(user)
                .title("Spring Batch 도입기")
                .content("Spring Batch를 프로젝트에 도입했다...")
                .level(Level.JUNIOR)
                .build();

        // then
        assertThat(post.getTitle()).isEqualTo("Spring Batch 도입기");
        assertThat(post.getUser()).isEqualTo(user);
        assertThat(post.getLevel()).isEqualTo(Level.JUNIOR);
    }
}
