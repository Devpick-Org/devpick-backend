package com.devpick.domain.community.client;

import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiAnswerClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record AiAnswerFastApiResponse(
            @JsonProperty("answer_content") String answerContent
    ) {}

    /**
     * AI 서버에 답변 생성을 요청한다.
     * refined 데이터가 있으면 그것을, 없으면 original 데이터를 refined 필드에도 전달한다.
     *
     * @param post       원본 게시글
     * @param aiQuestion refine 결과 (없으면 null)
     */
    public String generateAnswer(Post post, AiQuestion aiQuestion) {
        try {
            String refinedTitle = aiQuestion != null ? aiQuestion.getRefinedTitle() : post.getTitle();
            String refinedContent = aiQuestion != null ? aiQuestion.getRefinedContent() : post.getContent();

            Map<String, Object> body = new HashMap<>();
            body.put("refined_title", refinedTitle);
            body.put("refined_content", refinedContent);
            body.put("original_title", post.getTitle());
            body.put("original_content", post.getContent());
            body.put("question_id", post.getId().toString());
            body.put("user_id", post.getUser().getId().toString());

            AiAnswerFastApiResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/answer")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiAnswerFastApiResponse.class)
                    .block();

            if (response == null || response.answerContent() == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response.answerContent();
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
