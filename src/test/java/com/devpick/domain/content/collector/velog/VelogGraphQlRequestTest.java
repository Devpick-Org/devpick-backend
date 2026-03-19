package com.devpick.domain.content.collector.velog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VelogGraphQlRequestTest {

    @Test
    @DisplayName("trendingPosts — operationName이 TrendingPosts로 설정됨")
    void trendingPosts_operationName() {
        VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(0, 20, "week");
        assertThat(request.operationName()).isEqualTo("TrendingPosts");
    }

    @Test
    @DisplayName("trendingPosts — query에 trendingPosts 포함됨")
    void trendingPosts_queryContainsTrendingPosts() {
        VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(0, 20, "week");
        assertThat(request.query()).contains("trendingPosts");
    }

    @Test
    @DisplayName("trendingPosts — query에 TrendingPostsInput 래퍼 사용 (v3 필수)")
    void trendingPosts_queryUsesInputWrapper() {
        VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(0, 20, "week");
        assertThat(request.query()).contains("TrendingPostsInput");
        assertThat(request.query()).contains("$input");
        assertThat(request.query()).contains("input: $input");
    }

    @Test
    @DisplayName("trendingPosts — variables.input에 limit, offset, timeframe 올바르게 설정")
    void trendingPosts_variables() {
        VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(10, 20, "month");
        assertThat(request.variables().input().limit()).isEqualTo(20);
        assertThat(request.variables().input().offset()).isEqualTo(10);
        assertThat(request.variables().input().timeframe()).isEqualTo("month");
    }

    @Test
    @DisplayName("trendingPosts — timeframe day/week/month/year 모두 설정 가능")
    void trendingPosts_timeframeVariants() {
        for (String timeframe : new String[]{"day", "week", "month", "year"}) {
            VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(0, 20, timeframe);
            assertThat(request.variables().input().timeframe()).isEqualTo(timeframe);
        }
    }
}