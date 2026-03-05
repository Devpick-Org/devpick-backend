package com.devpick.global.entity;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    @DisplayName("onUpdate() - updatedAt 이 갱신되고 createdAt 은 유지된다")
    void onUpdate_refreshesOnlyUpdatedAt() throws Exception {
        // given: onCreate 후 updatedAt 을 과거로 강제 세팅
        User user = buildUser();
        invokeOnCreate(user);
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(5);
        setField("updatedAt", user, pastTime);

        // when
        invokeOnUpdate(user);

        // then
        assertThat(user.getUpdatedAt()).isAfter(pastTime);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("onCreate() 호출 시 createdAt 이 updatedAt 보다 늦지 않다")
    void onCreate_createdAtNotAfterUpdatedAt() throws Exception {
        // given
        User user = buildUser();

        // when
        invokeOnCreate(user);

        // then
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(user.getUpdatedAt());
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

    private void setField(String fieldName, Object target, Object value) throws Exception {
        Field field = BaseTimeEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
