package com.devpick.global.entity;

import com.devpick.domain.user.entity.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseCreatedEntity 단위 테스트")
class BaseCreatedEntityTest {

    @Test
    @DisplayName("onCreate() - createdAt 이 현재 시각으로 설정된다")
    void onCreate_setsCreatedAt() throws Exception {
        // given
        Tag tag = Tag.builder().name("spring").build();

        // when
        invokeOnCreate(tag);

        // then
        assertThat(tag.getCreatedAt()).isNotNull();
        assertThat(tag.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("onCreate() 중복 호출 시 createdAt 이 갱신된다")
    void onCreate_calledTwice_updatesCreatedAt() throws Exception {
        // given
        Tag tag = Tag.builder().name("java").build();
        invokeOnCreate(tag);
        LocalDateTime first = tag.getCreatedAt();

        // when
        Thread.sleep(10);
        invokeOnCreate(tag);

        // then
        assertThat(tag.getCreatedAt()).isAfterOrEqualTo(first);
    }

    private void invokeOnCreate(BaseCreatedEntity entity) throws Exception {
        Method method = BaseCreatedEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(entity);
    }
}
