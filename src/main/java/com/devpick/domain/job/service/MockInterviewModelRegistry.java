package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.MockInterviewModels.AvailableModelsResponse;
import com.devpick.domain.job.dto.MockInterviewModels.ModelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자에게 노출 가능한 Bedrock 모델 라벨 → 내부 모델 키 매핑.
 * AI 서버는 모델 키를 받아 Bedrock 호출 시 실제 model id로 다시 매핑한다.
 *
 * <p>키는 안정적이지만 라벨/설명은 UI 텍스트일 뿐이며, Opus 등 비용 높은 모델은
 * `app.mock-interview.allow-opus=true` 일 때만 옵션에 포함된다.</p>
 */
@Component
public class MockInterviewModelRegistry {

    public static final String DEFAULT_KEY = "balanced";

    private final boolean allowOpus;

    public MockInterviewModelRegistry(@Value("${app.mock-interview.allow-opus:false}") boolean allowOpus) {
        this.allowOpus = allowOpus;
    }

    public AvailableModelsResponse list() {
        return new AvailableModelsResponse(buildOptions(), DEFAULT_KEY);
    }

    public String resolveOrDefault(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_KEY;
        }
        Map<String, ModelOption> map = optionMap();
        String norm = key.toLowerCase();
        if (!map.containsKey(norm)) {
            return DEFAULT_KEY;
        }
        return norm;
    }

    private List<ModelOption> buildOptions() {
        return List.copyOf(optionMap().values());
    }

    private Map<String, ModelOption> optionMap() {
        Map<String, ModelOption> options = new LinkedHashMap<>();
        options.put("fast", new ModelOption(
                "fast",
                "빠름",
                "응답이 가장 빠르고, 비용/소진이 가장 적습니다. 간단한 답변 평가에 적합합니다.",
                false));
        options.put("balanced", new ModelOption(
                "balanced",
                "균형 (권장)",
                "Sonnet 계열을 사용하며, 답변 평가/꼬리질문 품질과 속도가 균형 잡혀 있습니다.",
                false));
        if (allowOpus) {
            options.put("deep", new ModelOption(
                    "deep",
                    "고성능",
                    "Opus 계열을 사용해 더 깊이 있는 평가가 가능하지만, 사용량 소진이 빠릅니다.",
                    true));
        }
        return options;
    }
}
