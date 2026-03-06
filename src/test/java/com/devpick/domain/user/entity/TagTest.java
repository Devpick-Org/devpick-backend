package com.devpick.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tag Entity 단위 테스트")
class TagTest {

    @Test
    @DisplayName("빌더로 Tag 를 생성할 수 있다")
    void builder_createsTag() {
        // given & when
        Tag tag = Tag.builder().name("spring-boot").build();

        // then
        assertThat(tag.getName()).isEqualTo("spring-boot");
        assertThat(tag.getId()).isNull(); // persist 전
    }
}
