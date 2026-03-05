package com.devpick.global.entity;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseTimeEntity 단위 테스트")
class BaseTimeEntityTest {

    @Test
    @DisplayName("onCreate() - createdAt, updatedAt 이 현재 시각으로 설정된다")
    void onCreate_setsTimestamps() throws Exception {
        // given
        User user = buildUser();

        // when
        invokeOnCreate(user);

        // then
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(user.getUpdatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("onUpdate() - updatedAt 만 갱신된다")
    void onUpdate_refreshesUpdatedAt() throws Exception {
        // given
        User user = buildUser();
        invokeOnCreate(user);
        LocalDateTime firstUpdatedAt = user.getUpdatedAt();

        // when
        Thread.sleep(10);
        invokeOnUpdate(user);

        // then
        assertThat(user.getUpdatedAt()).isAfter(firstUpdatedAt);
        assertThat(user.getCreatedAt()).isNotNull(); // createdAt 유지
    }

    @Test
    @DisplayName("onCreate() 호출 시 createdAt 이 updatedAt 보다 늦지 않다")
    void onCreate_createdAtNotAfterUpdatedAt() throws Exception {
        // given
        User user = buildUser();

        // when
        invokeOnCreate(user);

        // then
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

    private void invokeOnCreate(BaseTimeEntity entity) throws Exception {
        Method method = BaseTimeEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(entity);
    }

    private void invokeOnUpdate(BaseTimeEntity entity) throws Exception {
        Method method = BaseTimeEntity.class.getDeclaredMethod("onUpdate");
        method.setAccessible(true);
        method.invoke(entity);
    }
}
