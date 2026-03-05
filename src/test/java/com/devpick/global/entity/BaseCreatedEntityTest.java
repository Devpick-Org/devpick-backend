package com.devpick.global.entity;

import com.devpick.domain.user.entity.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    @DisplayName("onCreate() 재호출 시 createdAt 이 과거 값보다 같거나 이후이다")
    void onCreate_calledAgain_createdAtUpdated() throws Exception {
        // given: createdAt 을 과거로 강제 세팅
        Tag tag = Tag.builder().name("java").build();
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(5);
        setField("createdAt", tag, pastTime);

        // when
        invokeOnCreate(tag);

        // then
        assertThat(tag.getCreatedAt()).isAfterOrEqualTo(pastTime);
    }

    private void invokeOnCreate(BaseCreatedEntity entity) throws Exception {
        Method method = BaseCreatedEntity.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(entity);
    }

    private void setField(String fieldName, Object target, Object value) throws Exception {
        Field field = BaseCreatedEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
