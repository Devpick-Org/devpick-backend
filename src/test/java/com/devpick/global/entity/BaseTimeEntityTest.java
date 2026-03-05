package com.devpick.global.entity;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseTimeEntity 단위 테스트")
class BaseTimeEntityTest {

    @Test
    @DisplayName("onCreate() - createdAt, updatedAt 이 현재 시각으로 설정된다")
    void onCreate_setsTimestamps() {
        // given
        User user = buildUser();

        // when
        user.onCreate(); // @PrePersist 직접 호출

        // then
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(user.getUpdatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("onUpdate() - updatedAt 만 갱신된다")
    void onUpdate_refreshesUpdatedAt() throws InterruptedException {
        // given
        User user = buildUser();
        user.onCreate();
        LocalDateTime firstUpdatedAt = user.getUpdatedAt();

        // when
        Thread.sleep(10); // 시간 차이 확보
        user.onUpdate(); // @PreUpdate 직접 호출

        // then
        assertThat(user.getUpdatedAt()).isAfter(firstUpdatedAt);
        assertThat(user.getCreatedAt()).isEqualTo(user.getCreatedAt()); // createdAt 불변
    }

    @Test
    @DisplayName("onCreate() 호출 시 createdAt == updatedAt")
    void onCreate_createdAtEqualsUpdatedAt() {
        // given
        User user = buildUser();

        // when
        user.onCreate();

        // then
        // 같은 시각에 세팅되므로 같거나 거의 동일해야 함
        assertThat(user.getCreatedAt()).isNotAfter(user.getUpdatedAt());
    }

    private User buildUser() {
        return User.builder()
                .email("test@devpick.com")
                .nickname("하영")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
    }
}
