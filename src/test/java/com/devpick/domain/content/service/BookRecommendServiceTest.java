package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AladinBookClient;
import com.devpick.domain.content.dto.AladinBookDocument;
import com.devpick.domain.content.dto.BookRecommendResponse;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.UserTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookRecommendServiceTest {

    @InjectMocks private BookRecommendService bookRecommendService;
    @Mock private HistoryRepository historyRepository;
    @Mock private UserTagRepository userTagRepository;
    @Mock private AladinBookClient aladinBookClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private AladinBookDocument book(String isbn, String title) {
        return new AladinBookDocument(title, "저자", "출판사",
                "https://thumb.jpg", "https://url", "소개", isbn, 18000, 20000, 5000);
    }

    private AladinBookDocument bookWithSalesPoint(String isbn, String title, int salesPoint) {
        return new AladinBookDocument(title, "저자", "출판사",
                "https://thumb.jpg", "https://url", "소개", isbn, 18000, 20000, salesPoint);
    }

    private List<Object[]> tagRows(Object[]... rows) {
        return List.of(rows);
    }

    // ── getTagCountMap ────────────────────────────────────────────────────

    @Test
    @DisplayName("1개월 이력 있으면 그대로 반환")
    void getTagCountMap_oneMonth_returnsMap() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 3L}));

        Map<String, Long> result = bookRecommendService.getTagCountMap(userId);

        assertThat(result).containsEntry("Spring", 5L).containsEntry("Java", 3L);
        verify(historyRepository).findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any());
    }

    @Test
    @DisplayName("1개월 이력 없으면 3개월로 재조회")
    void getTagCountMap_empty1Month_expands3Month() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of())
                .willReturn(tagRows(new Object[]{"Kotlin", 2L}));

        Map<String, Long> result = bookRecommendService.getTagCountMap(userId);

        assertThat(result).containsEntry("Kotlin", 2L);
        verify(historyRepository, org.mockito.Mockito.times(2))
                .findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any());
    }

    // ── selectKeywordsByThreshold ─────────────────────────────────────────

    @Test
    @DisplayName("threshold=3 에서 3개 이상 → 그대로 선택")
    void selectKeywords_threshold3_returnsThree() {
        Map<String, Long> counts = Map.of("Spring", 5L, "Java", 4L, "JPA", 3L, "Docker", 2L);
        List<String> keywords = bookRecommendService.selectKeywordsByThreshold(counts, userId);
        assertThat(keywords).hasSize(3);
    }

    @Test
    @DisplayName("threshold=3 에서 2개 미만 → threshold=2로 완화")
    void selectKeywords_relaxesTo2() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("Spring", 4L);
        counts.put("Java", 3L);
        counts.put("Docker", 2L);
        List<String> keywords = bookRecommendService.selectKeywordsByThreshold(counts, userId);
        assertThat(keywords).hasSize(3);
    }

    @Test
    @DisplayName("threshold=1 에서도 3개 미만이면 전부 반환")
    void selectKeywords_fewerThan3Total_returnsAll() {
        Map<String, Long> counts = Map.of("Spring", 1L, "Java", 1L);
        List<String> keywords = bookRecommendService.selectKeywordsByThreshold(counts, userId);
        assertThat(keywords).hasSize(2);
    }

    // ── selectRandom ──────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 userId + 날짜 시드 → 동일한 결과")
    void selectRandom_sameSeed_sameResult() {
        List<String> candidates = List.of("A", "B", "C", "D", "E");
        List<String> r1 = bookRecommendService.selectRandom(candidates, userId, 3);
        List<String> r2 = bookRecommendService.selectRandom(candidates, userId, 3);
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("빈 목록이면 빈 리스트 반환")
    void selectRandom_emptyList_returnsEmpty() {
        assertThat(bookRecommendService.selectRandom(List.of(), userId, 3)).isEmpty();
    }

    // ── getRecommendBooks ─────────────────────────────────────────────────

    @Test
    @DisplayName("history 태그 있음 → 개인화 응답, isPersonalized=true")
    void getRecommendBooks_historyTags_returnsPersonalized() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 5L},
                        new Object[]{"Java", 4L},
                        new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString()))
                .willReturn(List.of(book("111", "책1"), book("222", "책2")));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.books()).isNotEmpty();
        verify(userTagRepository, never()).findByUser_Id(any());
    }

    @Test
    @DisplayName("history 없고 user_tags 있으면 → user_tags 기반 개인화")
    void getRecommendBooks_noHistory_usesUserTags() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("Spring").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(aladinBookClient.searchBooks(anyString()))
                .willReturn(List.of(book("111", "책1")));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.isPersonalized()).isTrue();
        verify(userTagRepository).findByUser_Id(userId);
    }

    @Test
    @DisplayName("history 없고 user_tags도 없으면 → 빈 배열, isPersonalized=false, message 포함")
    void getRecommendBooks_noTags_returnsNotEnoughMessage() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.books()).isEmpty();
        assertThat(result.message()).isEqualTo(BookRecommendService.NOT_ENOUGH_MESSAGE);
        verify(aladinBookClient, never()).searchBooks(anyString());
    }

    @Test
    @DisplayName("ISBN 중복 제거 — 같은 ISBN은 한 번만 포함")
    void getRecommendBooks_deduplicatesByIsbn() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 3L},
                        new Object[]{"Java", 3L},
                        new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(eq("Spring")))
                .willReturn(List.of(book("111", "중복책"), book("222", "책2")));
        given(aladinBookClient.searchBooks(eq("Java")))
                .willReturn(List.of(book("111", "중복책"), book("333", "책3")));
        given(aladinBookClient.searchBooks(eq("JPA")))
                .willReturn(List.of(book("444", "책4")));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        long count = result.books().stream().filter(b -> "중복책".equals(b.title())).count();
        assertThat(count).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("결과는 최대 8권")
    void getRecommendBooks_resultCappedAt8() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 5L},
                        new Object[]{"Java", 4L},
                        new Object[]{"JPA", 3L}));
        List<AladinBookDocument> manyBooks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyBooks.add(book("isbn" + i, "책" + i));
        }
        given(aladinBookClient.searchBooks(anyString())).willReturn(manyBooks);

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).hasSizeLessThanOrEqualTo(BookRecommendService.RESULT_SIZE);
    }

    @Test
    @DisplayName("알라딘 빈 응답이면 빈 books 반환 (개인화 true 유지)")
    void getRecommendBooks_aladinReturnsEmpty_emptyBooks() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 3L},
                        new Object[]{"Java", 3L},
                        new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of());

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.books()).isEmpty();
    }

    // ── buildResponse 필터 조건 ────────────────────────────────────────────

    @Test
    @DisplayName("썸네일(cover)이 blank인 도서는 결과에서 제외")
    void buildResponse_blankThumbnail_excluded() {
        AladinBookDocument noThumb = new AladinBookDocument(
                "썸네일없는책", "저자", "출판사",
                "", "https://url", "소개", "999", 18000, 20000, 5000);
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(noThumb));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).isEmpty();
    }

    @Test
    @DisplayName("priceSales=0 도서는 결과에서 제외")
    void buildResponse_zeroPriceBook_excluded() {
        AladinBookDocument freeBook = new AladinBookDocument(
                "무료책", "저자", "출판사",
                "https://thumb.jpg", "https://url", "소개", "777", 0, 20000, 5000);
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(freeBook));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).isEmpty();
    }

    @Test
    @DisplayName("키워드 하나당 최대 5권만 수집")
    void buildResponse_perKeywordLimit5() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        List<AladinBookDocument> manyBooks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            manyBooks.add(new AladinBookDocument(
                    "JPA책" + i, "저자", "출판사",
                    "https://thumb" + i + ".jpg", "https://url", "소개", "jpa" + i, 18000, 20000, 5000));
        }
        given(aladinBookClient.searchBooks(anyString())).willReturn(manyBooks);

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).hasSizeLessThanOrEqualTo(BookRecommendService.PER_KEYWORD_LIMIT);
    }

    @Test
    @DisplayName("salesPoint 높은 책이 우선 수집됨")
    void buildResponse_sortsBySalesPoint() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        List<AladinBookDocument> books = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            books.add(bookWithSalesPoint("isbn" + i, "책" + i, i * 1000));
        }
        given(aladinBookClient.searchBooks(anyString())).willReturn(books);

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        List<String> titles = result.books().stream().map(b -> b.title()).toList();
        assertThat(titles).contains("책9", "책8", "책7");
    }

    @Test
    @DisplayName("BlogBest에 있는 책이 결과에 포함됨")
    void buildResponse_blogBestIncluded() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(aladinBookClient.getBlogBestIsbnSet()).willReturn(Set.of("blogbest"));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(
                bookWithSalesPoint("other1", "일반책1", 9000),
                bookWithSalesPoint("blogbest", "블로그베스트책", 1000),
                bookWithSalesPoint("other2", "일반책2", 8000)
        ));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books().stream().anyMatch(b -> "블로그베스트책".equals(b.title()))).isTrue();
    }

    @Test
    @DisplayName("제목 앞부분이 같은 도서는 한 번만 포함")
    void buildResponse_titlePrefixDedup_excludesDuplicate() {
        AladinBookDocument book1 = new AladinBookDocument(
                "자바프로그래밍완전정복 1판", "저자", "출판사",
                "https://thumb1.jpg", "https://url1", "소개", "aa1", 18000, 20000, 5000);
        AladinBookDocument book2 = new AladinBookDocument(
                "자바프로그래밍완전정복 개정판", "저자", "출판사",
                "https://thumb2.jpg", "https://url2", "소개", "aa2", 18000, 20000, 4000);
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(book1, book2));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        long matchCount = result.books().stream()
                .filter(b -> b.title().startsWith("자바프로그래밍완전정복"))
                .count();
        assertThat(matchCount).isEqualTo(1);
    }

    @Test
    @DisplayName("ISBN이 blank인 도서는 중복 체크 없이 포함")
    void buildResponse_blankIsbn_includedWithoutDedup() {
        AladinBookDocument noIsbn1 = new AladinBookDocument(
                "isbn없는책1", "저자", "출판사",
                "https://thumb.jpg", "https://url", "소개", "", 18000, 20000, 5000);
        AladinBookDocument noIsbn2 = new AladinBookDocument(
                "isbn없는책2", "저자", "출판사",
                "https://thumb2.jpg", "https://url2", "소개2", "", 18000, 20000, 4000);
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(noIsbn1, noIsbn2));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        List<String> titles = result.books().stream().map(b -> b.title()).toList();
        assertThat(titles).contains("isbn없는책1", "isbn없는책2");
    }

    @Test
    @DisplayName("저자 문자열이 쉼표로 분리되어 authors 리스트로 반환됨")
    void buildResponse_authorSplitByComma() {
        AladinBookDocument bookDoc = new AladinBookDocument(
                "저자분리책", "홍길동, 번역자 (옮긴이)", "출판사",
                "https://thumb.jpg", "https://url", "소개", "bbb", 18000, 20000, 5000);
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(aladinBookClient.searchBooks(anyString())).willReturn(List.of(bookDoc));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books().get(0).authors()).contains("홍길동", "번역자 (옮긴이)");
    }

    // ── expandWithKorean ──────────────────────────────────────────────────

    @Test
    @DisplayName("변환 맵에 있는 태그는 영어+한글 둘 다 포함")
    void expandWithKorean_knownTag_addsKorean() {
        List<String> result = BookRecommendService.expandWithKorean(List.of("React", "Java"));
        assertThat(result).contains("React", "리액트", "Java", "자바");
    }

    @Test
    @DisplayName("변환 맵에 없는 태그는 원본만 포함")
    void expandWithKorean_unknownTag_keepsOriginal() {
        List<String> result = BookRecommendService.expandWithKorean(List.of("JPA"));
        assertThat(result).containsExactly("JPA");
    }

    // ── titlePrefix ───────────────────────────────────────────────────────

    @Test
    @DisplayName("공백 제거 후 10자 이상 제목 → 앞 10자 반환")
    void titlePrefix_longTitle_returns10Chars() {
        String prefix = BookRecommendService.titlePrefix("자바 프로그래밍 완전 정복 입문서");
        assertThat(prefix).hasSize(10);
        assertThat(prefix).doesNotContain(" ");
    }

    @Test
    @DisplayName("null 제목 → 빈 문자열 반환")
    void titlePrefix_null_returnsEmpty() {
        assertThat(BookRecommendService.titlePrefix(null)).isEmpty();
    }

    @Test
    @DisplayName("공백 제거 후 10자 미만 제목 → 전체 반환")
    void titlePrefix_shortTitle_returnsAll() {
        assertThat(BookRecommendService.titlePrefix("자바")).isEqualTo("자바");
    }

    @Test
    @DisplayName("공백 포함 제목 → 공백 제거 후 10자 prefix 동일하면 같은 prefix")
    void titlePrefix_spacesRemoved_samePrefix() {
        String p1 = BookRecommendService.titlePrefix("자바프로그래밍완전정복 1판");
        String p2 = BookRecommendService.titlePrefix("자바프로그래밍완전정복 개정판");
        assertThat(p1).isEqualTo(p2);
    }
}