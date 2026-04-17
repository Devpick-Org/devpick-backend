package com.devpick.domain.community.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * 질문(post) 삭제 후 AI 서버 DynamoDB·FAISS 정리 — {@code DELETE /internal/questions/{question_id}} (fire-and-forget).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuestionCleanupClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    /**
     * 응답을 기다리지 않는다. 실패는 로그만 남긴다.
     */
    public void notifyQuestionDeleted(UUID questionId) {
        if (internalKey == null || internalKey.isBlank()) {
            log.debug("Skip AI question cleanup — ai.server.internal-key not set");
            return;
        }
        webClient.delete()
                .uri(aiServerUrl + "/internal/questions/" + questionId)
                .header("X-Internal-Key", internalKey)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        r -> log.debug("AI question cleanup ok: questionId={}", questionId),
                        e -> log.warn("AI question cleanup failed: questionId={}, err={}", questionId, e.toString())
                );
    }
}
