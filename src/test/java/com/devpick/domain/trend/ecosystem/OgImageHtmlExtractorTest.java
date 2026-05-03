package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OgImageHtmlExtractorTest {

    @Test
    @DisplayName("og:relative 경로면 base 로 절대 URL 화한다")
    void resolvesRelativeOgImage() {
        String html = "<meta property=\"og:image\" content=\"/images/x.png\">";
        String abs = OgImageHtmlExtractor.extractPreferredImageHref(html, "https://example.org/club/");
        assertThat(abs).isEqualTo("https://example.org/images/x.png");
    }

    @Test
    @DisplayName("절대 og:image 유지한다")
    void keepsAbsoluteOgImage() {
        String html =
                "<meta property=\"og:image\" content=\"https://cdn.example.net/a.webp\">";
        String abs =
                OgImageHtmlExtractor.extractPreferredImageHref(html, "https://landing.example/");
        assertThat(abs).isEqualTo("https://cdn.example.net/a.webp");
    }
}
