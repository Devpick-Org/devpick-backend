package com.devpick.domain.community.client;

import com.devpick.domain.community.entity.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 게시글 생성 후 AI 서버 FAISS에 질문 임베딩 요청 — {@code POST /internal/questions} (fire-and-forget).
 * AI 답변 선택 여부와 무관하게 유사 질문 검색이 동작하도록 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionIndexClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public void indexQuestion(Post post) {
        if (internalKey == null || internalKey.isBlank()) {
            log.debug("Skip question indexing — ai.server.internal-key not set");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("question_id", post.getId().toString());
        body.put("title", post.getTitle());
        body.put("content", post.getContent());
        body.put("user_id", post.getUser().getId().toString());

        webClient.post()
                .uri(aiServerUrl + "/internal/questions")
                .header("X-Internal-Key", internalKey)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        r -> log.debug("Question indexed ok: postId={}", post.getId()),
                        e -> log.warn("Question indexing failed: postId={}, err={}", post.getId(), e.toString())
                );
    }
}
