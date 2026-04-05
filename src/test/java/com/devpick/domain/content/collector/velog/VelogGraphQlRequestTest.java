package com.devpick.domain.content.collector.velog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VelogGraphQlRequestTest {

    @Test
    @DisplayName("recentPosts — operationName이 Posts로 설정됨")
    void recentPosts_operationName() {
        VelogGraphQlRequest request = VelogGraphQlRequest.recentPosts();
        assertThat(request.operationName()).isEqualTo("Posts");
    }

    @Test
    @DisplayName("recentPosts — query에 posts 포함됨")
    void recentPosts_queryContainsPosts() {
        VelogGraphQlRequest request = VelogGraphQlRequest.recentPosts();
        assertThat(request.query()).contains("posts");
    }

    @Test
    @DisplayName("recentPosts — variables가 null임")
    void recentPosts_variablesNull() {
        VelogGraphQlRequest request = VelogGraphQlRequest.recentPosts();
        assertThat(request.variables()).isNull();
    }
}