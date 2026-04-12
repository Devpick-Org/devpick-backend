package com.devpick.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownPreviewUtilsTest {

    @Test
    @DisplayName("stripForPreview — 헤더·강조·코드 펜스 제거")
    void stripForPreview_removesCommonMarkdown() {
        String raw = "## 제목\n\n`코드`와 **굵게** 그리고 [링크](https://x.com) 끝";
        String out = MarkdownPreviewUtils.stripForPreview(raw);
        assertThat(out).doesNotContain("##");
        assertThat(out).doesNotContain("**");
        assertThat(out).doesNotContain("https://x.com");
        assertThat(out).contains("링크");
    }

    @Test
    @DisplayName("stripForPreview — null 은 그대로")
    void stripForPreview_null_returnsNull() {
        assertThat(MarkdownPreviewUtils.stripForPreview(null)).isNull();
    }
}
