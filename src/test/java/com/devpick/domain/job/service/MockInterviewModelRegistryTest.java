package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.MockInterviewModels.AvailableModelsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewModelRegistryTest {

    @Test
    @DisplayName("기본 옵션은 fast/balanced 2개이며 balanced가 기본값")
    void list_defaultsTwoOptions() {
        MockInterviewModelRegistry registry = new MockInterviewModelRegistry(false);
        AvailableModelsResponse res = registry.list();
        assertThat(res.defaultKey()).isEqualTo("balanced");
        assertThat(res.models()).extracting("key").containsExactly("fast", "balanced");
    }

    @Test
    @DisplayName("Opus 허용 시 deep 옵션이 추가된다")
    void list_deepIncludedWhenAllowed() {
        MockInterviewModelRegistry registry = new MockInterviewModelRegistry(true);
        AvailableModelsResponse res = registry.list();
        assertThat(res.models()).extracting("key").containsExactly("fast", "balanced", "deep");
        assertThat(res.models())
                .filteredOn("key", "deep")
                .extracting("experimental")
                .containsExactly(true);
    }

    @Test
    @DisplayName("알 수 없는 키는 기본값으로 폴백한다")
    void resolveOrDefault_fallsBackForUnknownKey() {
        MockInterviewModelRegistry registry = new MockInterviewModelRegistry(false);
        assertThat(registry.resolveOrDefault("unknown")).isEqualTo("balanced");
        assertThat(registry.resolveOrDefault("FAST")).isEqualTo("fast");
        assertThat(registry.resolveOrDefault(null)).isEqualTo("balanced");
        assertThat(registry.resolveOrDefault("deep")).isEqualTo("balanced");
    }
}
