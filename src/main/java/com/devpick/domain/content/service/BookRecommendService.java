package com.devpick.domain.content.service;

import com.devpick.domain.content.client.KakaoBookClient;
import com.devpick.domain.content.dto.BookItem;
import com.devpick.domain.content.dto.BookRecommendResponse;
import com.devpick.domain.content.dto.KakaoBookDocument;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookRecommendService {

    static final String NOT_ENOUGH_MESSAGE = "아직 추천할 도서가 부족해요. 더 많은 글을 읽어보세요!";
    static final List<String> HISTORY_ACTION_TYPES =
            List.of("scrapped", "ai_summary_viewed", "ai_quiz_completed", "content_liked");
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int KEYWORD_COUNT = 3;
    static final int RESULT_SIZE = 10;
    static final int MIN_PUBLISH_YEAR = 2020;

    private final HistoryRepository historyRepository;
    private final UserTagRepository userTagRepository;
    private final KakaoBookClient kakaoBookClient;

    @Transactional(readOnly = true)
    public BookRecommendResponse getRecommendBooks(UUID userId) {
        Map<String, Long> tagCounts = getTagCountMap(userId);

        if (!tagCounts.isEmpty()) {
            List<String> keywords = selectKeywordsByThreshold(tagCounts, userId);
            return buildResponse(keywords, userId, true);
        }

        List<String> userTagNames = userTagRepository.findByUser_Id(userId).stream()
                .map(ut -> ut.getTag().getName())
                .toList();

        if (!userTagNames.isEmpty()) {
            List<String> keywords = selectRandom(userTagNames, userId, KEYWORD_COUNT);
            return buildResponse(keywords, userId, true);
        }

        return new BookRecommendResponse(List.of(), false, NOT_ENOUGH_MESSAGE);
    }

    Map<String, Long> getTagCountMap(UUID userId) {
        List<Object[]> rows = historyRepository.findTagNameCountsByUserActionsAfter(
                userId, HISTORY_ACTION_TYPES, LocalDateTime.now(KST).minusMonths(1));
        if (rows.isEmpty()) {
            rows = historyRepository.findTagNameCountsByUserActionsAfter(
                    userId, HISTORY_ACTION_TYPES, LocalDateTime.now(KST).minusMonths(3));
        }
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    List<String> selectKeywordsByThreshold(Map<String, Long> tagCounts, UUID userId) {
        for (int minCount : List.of(3, 2, 1)) {
            List<String> candidates = tagCounts.entrySet().stream()
                    .filter(e -> e.getValue() >= minCount)
                    .map(Map.Entry::getKey)
                    .toList();
            if (candidates.size() >= KEYWORD_COUNT) {
                return selectRandom(candidates, userId, KEYWORD_COUNT);
            }
        }
        return selectRandom(new ArrayList<>(tagCounts.keySet()), userId, tagCounts.size());
    }

    List<String> selectRandom(List<String> candidates, UUID userId, int count) {
        if (candidates.isEmpty()) return List.of();
        long seed = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits()
                ^ LocalDate.now(KST).toEpochDay();
        List<String> copy = new ArrayList<>(candidates);
        Collections.shuffle(copy, new Random(seed)); // NOSONAR java:S2245
        return copy.subList(0, Math.min(count, copy.size()));
    }

    private BookRecommendResponse buildResponse(List<String> keywords, UUID userId, boolean isPersonalized) {
        List<KakaoBookDocument> merged = keywords.stream()
                .flatMap(kw -> kakaoBookClient.searchBooks(kw).stream())
                .toList();

        Set<String> seenIsbn = new LinkedHashSet<>();
        List<KakaoBookDocument> unique = merged.stream()
                .filter(doc -> doc.thumbnail() != null && !doc.thumbnail().isBlank())
                .filter(doc -> {
                    if (doc.datetime() == null || doc.datetime().isBlank()) return true;
                    try {
                        int year = Integer.parseInt(doc.datetime().substring(0, 4));
                        return year >= MIN_PUBLISH_YEAR;
                    } catch (NumberFormatException e) {
                        return true;
                    }
                })
                .filter(doc -> {
                    if (doc.isbn() == null || doc.isbn().isBlank()) return true;
                    return seenIsbn.add(doc.isbn().split(" ")[0]);
                })
                .toList();

        long seed = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits()
                ^ LocalDate.now(KST).toEpochDay();
        List<KakaoBookDocument> shuffled = new ArrayList<>(unique);
        Collections.shuffle(shuffled, new Random(seed)); // NOSONAR java:S2245

        List<BookItem> books = shuffled.subList(0, Math.min(RESULT_SIZE, shuffled.size()))
                .stream()
                .map(doc -> new BookItem(
                        doc.title(), doc.authors(), doc.publisher(),
                        doc.thumbnail(), doc.url(), doc.contents(),
                        doc.price(), doc.salePrice()))
                .toList();

        return new BookRecommendResponse(books, isPersonalized, null);
    }
}
