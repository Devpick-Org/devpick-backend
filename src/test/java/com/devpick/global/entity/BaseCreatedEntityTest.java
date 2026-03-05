package com.devpick.global.entity;

import com.devpick.domain.user.entity.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseCreatedEntity 단위 테스트")
class BaseCreatedEntityTest {

    @Test
    @DisplayName("onCreate() - createdAt 이 현재 시각으로 설정된다")
    void onCreate_setsCreatedAt() {
        // given
        Tag tag = Tag.builder().name("spring").build();

        // when
        tag.onCreate(); // @PrePersist 직접 호출

        // then
        assertThat(tag.getCreatedAt()).isNotNull();
        assertThat(tag.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("onCreate() 중복 호출 시 createdAt 이 갱신된다")
    void onCreate_calledTwice_updatesCreatedAt() throws InterruptedException {
        // given
        Tag tag = Tag.builder().name("java").build();
        tag.onCreate();
        LocalDateTime first = tag.getCreatedAt();

        // when
        Thread.sleep(10);
        tag.onCreate();

        // then
        assertThat(tag.getCreatedAt()).isAfterOrEqualTo(first);
    }
}
