package com.devpick.domain.content.collector.stackoverflow;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stack Overflow API v2.3 수집기.
 * CC BY-SA 4.0 — 저자명 + 원문 링크 표시 조건으로 원문 표시 허용.
 * API 키 없으면 일일 300회, 있으면 10,000회 쿼터.
 *
 * <p>수집 전략 (DP-200):
 * <ul>
 *   <li>sort=votes — 추천수 기준 정렬</li>
 *   <li>fromdate=30일 전 — 최근 1달 이내 게시물만</li>
 *   <li>answered 질문에 한해 배치로 답변 수집 (1회 API 호출)</li>
 *   <li>acceptedAnswer + score 상위 2개 topAnswers 구조화 저장</li>
 * </ul>
 */
@Slf4j
@Component
public class StackOverflowCollector extends ContentCollector {

    private static final String SOURCE_NAME = "Stack Overflow";
    private static final String BASE_URL = "https://api.stackexchange.com/2.3";
    private static final String SITE = "stackoverflow";
    private static final String LICENSE_TYPE = "CC BY-SA 4.0";
    private static final int PAGE_SIZE = 20;
    private static final int TOP_ANSWER_LIMIT = 2;
    private static final int PREVIEW_MAX_LENGTH = 300;

    private final WebClient webClient;

    @Value("${stackoverflow.api-key:}")
    private String apiKey;

    public StackOverflowCollector(WebClient webClient,
                                  ContentRepository contentRepository,
                                  ContentSourceRepository contentSourceRepository) {
        super(contentRepository, contentSourceRepository);
        this.webClient = webClient;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<CollectedContent> fetchItems(String query) {
        return fetchQuestions(query);
    }

    List<CollectedContent> fetchQuestions(String tags) {
        try {
            StackOverflowApiResponse response = webClient.get()
                    .uri(buildQuestionUri(tags))
                    .retrieve()
                    .bodyToMono(StackOverflowApiResponse.class)
                    .block();

            if (response == null || response.items() == null) {
                log.warn("Stack Overflow API returned null response.");
                return List.of();
            }

            log.info("Stack Overflow API quota remaining: {}", response.quotaRemaining());
            List<StackOverflowQuestion> questions = response.items();

            // answered 질문만 배치로 답변 수집 (1회 API 호출)
            List<Long> answeredIds = questions.stream()
                    .filter(StackOverflowQuestion::isAnswered)
                    .map(StackOverflowQuestion::questionId)
                    .toList();

            Map<Long, List<StackOverflowAnswer>> answersMap = answeredIds.isEmpty()
                    ? Map.of()
                    : fetchAnswersBatch(answeredIds);

            return questions.stream()
                    .map(q -> toCollectedContent(q, answersMap.getOrDefault(q.questionId(), List.of())))
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("Stack Overflow API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Stack Overflow API call failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 여러 질문의 답변을 한 번에 배치 조회한다.
     * 실패 시 빈 맵을 반환하여 질문 수집 자체는 계속 진행된다.
     *
     * @return questionId → answers 맵
     */
    Map<Long, List<StackOverflowAnswer>> fetchAnswersBatch(List<Long> questionIds) {
        String ids = questionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(";"));
        try {
            StackOverflowAnswerApiResponse response = webClient.get()
                    .uri(buildAnswerUri(ids))
                    .retrieve()
                    .bodyToMono(StackOverflowAnswerApiResponse.class)
                    .block();

            if (response == null || response.items() == null) {
                log.warn("Stack Overflow Answers API returned null for questionIds: {}", questionIds);
                return Map.of();
            }

            return response.items().stream()
                    .collect(Collectors.groupingBy(StackOverflowAnswer::questionId));

        } catch (Exception e) {
            log.warn("Failed to fetch answers for questionIds={}: {}", questionIds, e.getMessage());
            return Map.of();
        }
    }

    private String buildQuestionUri(String tags) {
        long fromdate = Instant.now().minus(30, ChronoUnit.DAYS).getEpochSecond();

        StringBuilder uri = new StringBuilder(BASE_URL)
                .append("/questions")
                .append("?order=desc")
                .append("&sort=votes")
                .append("&site=").append(SITE)
                .append("&filter=withbody")
                .append("&fromdate=").append(fromdate)
                .append("&pagesize=").append(PAGE_SIZE);

        if (tags != null && !tags.isBlank()) {
            uri.append("&tagged=").append(tags);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            uri.append("&key=").append(apiKey);
        }
        return uri.toString();
    }

    private String buildAnswerUri(String questionIds) {
        StringBuilder uri = new StringBuilder(BASE_URL)
                .append("/questions/").append(questionIds).append("/answers")
                .append("?order=desc")
                .append("&sort=votes")
                .append("&site=").append(SITE)
                .append("&filter=withbody")
                .append("&pagesize=100");

        if (apiKey != null && !apiKey.isBlank()) {
            uri.append("&key=").append(apiKey);
        }
        return uri.toString();
    }

    private CollectedContent toCollectedContent(StackOverflowQuestion q, List<StackOverflowAnswer> answers) {
        String author = (q.owner() != null && q.owner().displayName() != null)
                ? q.owner().displayName()
                : "Unknown";

        String preview = q.bodyMarkdown() != null && q.bodyMarkdown().length() > PREVIEW_MAX_LENGTH
                ? q.bodyMarkdown().substring(0, PREVIEW_MAX_LENGTH) + "..."
                : q.bodyMarkdown();

        LocalDateTime publishedAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(q.creationDate()), ZoneId.of("UTC"));

        // bodyMarkdown이 null인 답변은 제외 (filter=withbody 누락 대비)
        List<StackOverflowAnswer> validAnswers = answers.stream()
                .filter(a -> a.bodyMarkdown() != null)
                .toList();

        StackOverflowAnswerDto acceptedAnswer = validAnswers.stream()
                .filter(StackOverflowAnswer::isAccepted)
                .findFirst()
                .map(a -> new StackOverflowAnswerDto(a.bodyMarkdown(), a.score()))
                .orElse(null);

        List<StackOverflowAnswerDto> topAnswers = validAnswers.stream()
                .filter(a -> !a.isAccepted())
                .sorted(Comparator.comparingInt(StackOverflowAnswer::score).reversed())
                .limit(TOP_ANSWER_LIMIT)
                .map(a -> new StackOverflowAnswerDto(a.bodyMarkdown(), a.score()))
                .toList();

        String originalContent = buildOriginalContent(q.bodyMarkdown(), acceptedAnswer, topAnswers);

        return new CollectedContent(
                q.title(),
                author,
                q.link(),
                preview,
                originalContent,
                true,
                LICENSE_TYPE,
                publishedAt,
                q.tags() != null ? q.tags() : List.of(),
                q.score(),
                q.viewCount(),
                q.isAnswered(),
                q.bodyMarkdown(),
                acceptedAnswer,
                topAnswers.isEmpty() ? null : topAnswers
        );
    }

    /**
     * 질문 본문 + 답변을 하나의 텍스트로 합쳐 AI 요약용 originalContent를 생성한다.
     */
    private String buildOriginalContent(String bodyMarkdown,
                                        StackOverflowAnswerDto acceptedAnswer,
                                        List<StackOverflowAnswerDto> topAnswers) {
        if (bodyMarkdown == null && acceptedAnswer == null && topAnswers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (bodyMarkdown != null) {
            sb.append("## Question\n").append(bodyMarkdown).append("\n\n");
        }
        if (acceptedAnswer != null && acceptedAnswer.body() != null) {
            sb.append("## Accepted Answer\n").append(acceptedAnswer.body()).append("\n\n");
        }
        if (!topAnswers.isEmpty()) {
            sb.append("## Top Answers\n");
            for (StackOverflowAnswerDto answer : topAnswers) {
                sb.append(answer.body()).append("\n\n");
            }
        }
        return sb.toString().strip();
    }
}
