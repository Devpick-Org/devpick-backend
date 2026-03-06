package com.devpick.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Entity 단위 테스트")
class UserTest {

    @Test
    @DisplayName("빌더 기본값 - isActive=true, isEmailVerified=false")
    void builder_defaultValues() {
        // given & when
        User user = User.builder()
                .email("hay@devpick.com")
                .nickname("하영")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();

        // then
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getIsEmailVerified()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getUserTags()).isEmpty();
    }

    @Test
    @DisplayName("빌더로 모든 필드를 설정할 수 있다")
    void builder_allFields() {
        // given & when
        User user = User.builder()
                .email("admin@devpick.com")
                .nickname("어드민")
                .passwordHash("hashed_pw")
                .profileImage("https://example.com/img.png")
                .job(Job.FRONTEND)
                .level(Level.SENIOR)
                .isActive(false)
                .isEmailVerified(true)
                .build();

        // then
        assertThat(user.getEmail()).isEqualTo("admin@devpick.com");
        assertThat(user.getNickname()).isEqualTo("어드민");
        assertThat(user.getJob()).isEqualTo(Job.FRONTEND);
        assertThat(user.getLevel()).isEqualTo(Level.SENIOR);
        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getIsEmailVerified()).isTrue();
    }
}
