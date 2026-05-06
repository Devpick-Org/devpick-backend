package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AladinBookClient;
import com.devpick.domain.content.dto.AladinBookDocument;
import com.devpick.domain.content.dto.BookItem;
import com.devpick.domain.content.dto.BookRecommendResponse;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
    static final int RESULT_SIZE = 8;
    static final int PER_KEYWORD_LIMIT = 5;
    static final Map<String, String> KOREAN_TAG_MAP = Map.ofEntries(
            Map.entry("react", "리액트"),
            Map.entry("java", "자바"),
            Map.entry("javascript", "자바스크립트"),
            Map.entry("python", "파이썬"),
            Map.entry("spring", "스프링"),
            Map.entry("kotlin", "코틀린"),
            Map.entry("typescript", "타입스크립트"),
            Map.entry("docker", "도커"),
            Map.entry("kubernetes", "쿠버네티스"),
            Map.entry("linux", "리눅스"),
            Map.entry("algorithm", "알고리즘"),
            Map.entry("vue", "뷰"),
            Map.entry("vue.js", "뷰"),
            Map.entry("next.js", "넥스트"),
            Map.entry("nodejs", "노드"),
            Map.entry("node.js", "노드"),
            Map.entry("android", "안드로이드")
    );

    private final HistoryRepository historyRepository;
    private final UserTagRepository userTagRepository;
    private final AladinBookClient aladinBookClient;

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

    static List<String> expandWithKorean(List<String> keywords) {
        List<String> expanded = new ArrayList<>(keywords);
        for (String kw : keywords) {
            String korean = KOREAN_TAG_MAP.get(kw.toLowerCase());
            if (korean != null) expanded.add(korean);
        }
        return expanded;
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
        long seed = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits()
                ^ LocalDate.now(KST).toEpochDay();

        Set<String> blogBestIsbnSet = aladinBookClient.getBlogBestIsbnSet();

        Set<String> seenIsbn = new LinkedHashSet<>();
        Set<String> seenTitlePrefix = new LinkedHashSet<>();
        List<AladinBookDocument> priority = new ArrayList<>();
        List<AladinBookDocument> secondary = new ArrayList<>();

        for (String kw : expandWithKorean(keywords)) {
            List<AladinBookDocument> results = aladinBookClient.searchBooks(kw).stream()
                    .filter(doc -> doc.cover() != null && !doc.cover().isBlank())
                    .filter(doc -> doc.priceSales() > 0)
                    .sorted((a, b) -> Integer.compare(b.salesPoint(), a.salesPoint()))
                    .filter(doc -> {
                        if (doc.isbn13() == null || doc.isbn13().isBlank()) return true;
                        return seenIsbn.add(doc.isbn13());
                    })
                    .filter(doc -> seenTitlePrefix.add(titlePrefix(doc.title())))
                    .limit(PER_KEYWORD_LIMIT)
                    .toList();

            for (AladinBookDocument doc : results) {
                if (doc.isbn13() != null && blogBestIsbnSet.contains(doc.isbn13())) {
                    priority.add(doc);
                } else {
                    secondary.add(doc);
                }
            }
        }

        Collections.shuffle(priority, new Random(seed)); // NOSONAR java:S2245
        Collections.shuffle(secondary, new Random(seed)); // NOSONAR java:S2245

        List<AladinBookDocument> merged = new ArrayList<>(priority);
        merged.addAll(secondary);

        List<BookItem> books = merged.subList(0, Math.min(RESULT_SIZE, merged.size()))
                .stream()
                .map(this::toBookItem)
                .toList();

        return new BookRecommendResponse(books, isPersonalized, null);
    }

    private BookItem toBookItem(AladinBookDocument doc) {
        List<String> authors = (doc.author() != null && !doc.author().isBlank())
                ? Arrays.stream(doc.author().split(",")).map(String::trim).toList()
                : List.of();
        return new BookItem(
                doc.title(), authors, doc.publisher(),
                doc.cover(), doc.link(), doc.description(),
                doc.priceStandard(), doc.priceSales());
    }

    static String titlePrefix(String title) {
        if (title == null) return "";
        return title.replaceAll("\\s+", "").substring(0, Math.min(10, title.replaceAll("\\s+", "").length()));
    }
}
