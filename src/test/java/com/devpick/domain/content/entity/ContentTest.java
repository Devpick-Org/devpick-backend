package com.devpick.domain.content.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Content Entity 단위 테스트")
class ContentTest {

    @Test
    @DisplayName("빌더 기본값 - isOriginalVisible=false, isAvailable=true")
    void builder_defaultValues() {
        // given
        ContentSource source = ContentSource.builder()
                .name("Velog")
                .url("https://v2.velog.io/graphql")
                .collectMethod("graphql")
                .build();

        // when
        Content content = Content.builder()
                .source(source)
                .title("JPA 완전 정복")
                .canonicalUrl("https://velog.io/@user/jpa")
                .build();

        // then
        assertThat(content.getIsOriginalVisible()).isFalse();
        assertThat(content.getIsAvailable()).isTrue();
        assertThat(content.getContentTags()).isEmpty();
        assertThat(content.getTakedownRequestedAt()).isNull();
    }

    @Test
    @DisplayName("빌더로 주요 필드를 설정할 수 있다")
    void builder_withFields() {
        // given
        ContentSource source = ContentSource.builder()
                .name("Stack Overflow")
                .url("https://api.stackexchange.com")
                .collectMethod("api")
                .build();

        // when
        Content content = Content.builder()
                .source(source)
                .title("Spring Security 설정")
                .author("홍길동")
                .canonicalUrl("https://stackoverflow.com/q/12345")
                .isOriginalVisible(true)
                .licenseType("CC BY-SA 4.0")
                .build();

        // then
        assertThat(content.getTitle()).isEqualTo("Spring Security 설정");
        assertThat(content.getAuthor()).isEqualTo("홍길동");
        assertThat(content.getIsOriginalVisible()).isTrue();
        assertThat(content.getLicenseType()).isEqualTo("CC BY-SA 4.0");
    }

    @Test
    @DisplayName("extra 필드 미설정 시 null — 기존 콘텐츠 조회에 영향 없음")
    void builder_extraDefaultsToNull() {
        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://v2.velog.io/graphql").collectMethod("graphql").build();

        Content content = Content.builder()
                .source(source)
                .title("Kotlin 코루틴")
                .canonicalUrl("https://velog.io/@user/kotlin")
                .build();

        assertThat(content.getExtra()).isNull();
    }

    @Test
    @DisplayName("extra 필드에 YouTube JSON 문자열을 저장할 수 있다")
    void builder_extraWithYouTubeJson() {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://www.youtube.com").collectMethod("api").build();
        String youtubeExtra = "{\"videoId\":\"dQw4w9WgXcQ\",\"channelName\":\"우아한Tech\",\"duration\":312,\"viewCount\":15000}";

        Content content = Content.builder()
                .source(source)
                .title("우아한Tech 스프링 강의")
                .canonicalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .extra(youtubeExtra)
                .build();

        assertThat(content.getExtra()).isEqualTo(youtubeExtra);
        assertThat(content.getExtra()).contains("videoId");
        assertThat(content.getExtra()).contains("channelName");
    }
}
