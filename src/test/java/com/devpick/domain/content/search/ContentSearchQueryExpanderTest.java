package com.devpick.domain.content.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSearchQueryExpanderTest {

    @Test
    @DisplayName("넷플릭스 → netflix 포함")
    void koreanBrandExpandsEnglish() {
        List<String> out = ContentSearchQueryExpander.expand("넷플릭스");
        assertThat(out).containsExactly("넷플릭스", "netflix");
    }

    @Test
    @DisplayName("netflix → 넷플릭스 포함")
    void englishBrandExpandsKorean() {
        List<String> out = ContentSearchQueryExpander.expand("NETFLIX");
        assertThat(out).containsExactly("netflix", "넷플릭스");
    }

    @Test
    @DisplayName("동의어와 무관한 문구면 원문만")
    void noSynonymLeavesSingleNormalizedTerm() {
        assertThat(ContentSearchQueryExpander.expand("  Spring Boot  "))
                .containsExactly("spring boot");
    }

    @Test
    @DisplayName("공백·null은 빈 목록")
    void blankMeansNoKeywords() {
        assertThat(ContentSearchQueryExpander.expand(null)).isEmpty();
        assertThat(ContentSearchQueryExpander.expand("   ")).isEmpty();
    }
}
