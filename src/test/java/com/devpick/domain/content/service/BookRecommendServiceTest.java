package com.devpick.domain.content.service;

import com.devpick.domain.content.client.KakaoBookClient;
import com.devpick.domain.content.dto.BookRecommendResponse;
import com.devpick.domain.content.dto.KakaoBookDocument;
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
    @Mock private KakaoBookClient kakaoBookClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private KakaoBookDocument book(String isbn, String title) {
        return new KakaoBookDocument(title, List.of("저자"), "출판사",
                "https://thumb.jpg", "https://url", "소개", isbn, 20000, 18000, "2023-01-01T00:00:00.000+09:00");
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
        // threshold=3: Spring, Java (2개) → 미달 → threshold=2: Spring, Java, Docker (3개)
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
        given(kakaoBookClient.searchBooks(anyString()))
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
        given(kakaoBookClient.searchBooks(anyString()))
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
        verify(kakaoBookClient, never()).searchBooks(anyString());
    }

    @Test
    @DisplayName("ISBN 중복 제거 — 같은 ISBN은 한 번만 포함")
    void getRecommendBooks_deduplicatesByIsbn() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 3L},
                        new Object[]{"Java", 3L},
                        new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(eq("Spring")))
                .willReturn(List.of(book("111", "중복책"), book("222", "책2")));
        given(kakaoBookClient.searchBooks(eq("Java")))
                .willReturn(List.of(book("111", "중복책"), book("333", "책3")));
        given(kakaoBookClient.searchBooks(eq("JPA")))
                .willReturn(List.of(book("444", "책4")));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        long count111 = result.books().stream()
                .filter(b -> "중복책".equals(b.title())).count();
        assertThat(count111).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("결과는 최대 10권")
    void getRecommendBooks_resultCappedAt10() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 5L},
                        new Object[]{"Java", 4L},
                        new Object[]{"JPA", 3L}));

        List<KakaoBookDocument> manyBooks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyBooks.add(book("isbn" + i, "책" + i));
        }
        given(kakaoBookClient.searchBooks(anyString())).willReturn(manyBooks);

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("카카오 API 빈 응답이면 빈 books 반환 (개인화 true 유지)")
    void getRecommendBooks_kakaoReturnsEmpty_emptyBooks() {
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(
                        new Object[]{"Spring", 3L},
                        new Object[]{"Java", 3L},
                        new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of());

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.books()).isEmpty();
    }

    // ── buildResponse 필터 조건 ────────────────────────────────────────────

    @Test
    @DisplayName("썸네일이 blank인 도서는 결과에서 제외")
    void buildResponse_blankThumbnail_excluded() {
        KakaoBookDocument noThumb = new KakaoBookDocument(
                "썸네일없는책", List.of("저자"), "출판사",
                "", "https://url", "소개", "999", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of(noThumb));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).isEmpty();
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

    @Test
    @DisplayName("ISBN이 blank인 도서는 중복 체크 없이 포함")
    void buildResponse_blankIsbn_includedWithoutDedup() {
        KakaoBookDocument noIsbn1 = new KakaoBookDocument(
                "isbn없는책1", List.of("저자"), "출판사",
                "https://thumb.jpg", "https://url", "소개", "", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        KakaoBookDocument noIsbn2 = new KakaoBookDocument(
                "isbn없는책2", List.of("저자"), "출판사",
                "https://thumb2.jpg", "https://url2", "소개2", "", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of(noIsbn1, noIsbn2));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        // blank ISBN은 중복 제거 대상이 아니므로 두 책 모두 결과에 포함됨
        List<String> titles = result.books().stream().map(b -> b.title()).toList();
        assertThat(titles).contains("isbn없는책1", "isbn없는책2");
    }

    // ── hasKorean ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("한글 포함 텍스트 → true")
    void hasKorean_koreanText_returnsTrue() {
        assertThat(BookRecommendService.hasKorean("자바 프로그래밍")).isTrue();
    }

    @Test
    @DisplayName("영문만 있는 텍스트 → false")
    void hasKorean_englishOnly_returnsFalse() {
        assertThat(BookRecommendService.hasKorean("Java Programming")).isFalse();
    }

    @Test
    @DisplayName("null 텍스트 → false")
    void hasKorean_null_returnsFalse() {
        assertThat(BookRecommendService.hasKorean(null)).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 → false")
    void hasKorean_blank_returnsFalse() {
        assertThat(BookRecommendService.hasKorean("   ")).isFalse();
    }

    // ── containsKorean ────────────────────────────────────────────────────

    @Test
    @DisplayName("제목에 한글 → true")
    void containsKorean_koreanTitle_returnsTrue() {
        KakaoBookDocument doc = new KakaoBookDocument(
                "자바 입문", List.of("John"), "Publisher",
                "https://thumb.jpg", "https://url", "Java basics", "111", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        assertThat(BookRecommendService.containsKorean(doc)).isTrue();
    }

    @Test
    @DisplayName("저자에 한글(번역자 포함) → true")
    void containsKorean_koreanAuthor_returnsTrue() {
        KakaoBookDocument doc = new KakaoBookDocument(
                "Clean Code", List.of("홍길동 (옮긴이)"), "Prentice Hall",
                "https://thumb.jpg", "https://url", "Agile craftsmanship", "222", 40000, 35000, "2023-01-01T00:00:00.000+09:00");
        assertThat(BookRecommendService.containsKorean(doc)).isTrue();
    }

    @Test
    @DisplayName("출판사에 한글 → true")
    void containsKorean_koreanPublisher_returnsTrue() {
        KakaoBookDocument doc = new KakaoBookDocument(
                "Spring in Action", List.of("Craig Walls"), "한빛미디어",
                "https://thumb.jpg", "https://url", "Spring framework guide", "333", 30000, 27000, "2023-01-01T00:00:00.000+09:00");
        assertThat(BookRecommendService.containsKorean(doc)).isTrue();
    }

    @Test
    @DisplayName("소개에 한글 → true")
    void containsKorean_koreanContents_returnsTrue() {
        KakaoBookDocument doc = new KakaoBookDocument(
                "Java Book", List.of("John"), "Publisher",
                "https://thumb.jpg", "https://url", "자바 기초 학습 가이드", "444", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        assertThat(BookRecommendService.containsKorean(doc)).isTrue();
    }

    @Test
    @DisplayName("제목/저자/출판사/소개 모두 한글 없음 → false")
    void containsKorean_noKorean_returnsFalse() {
        KakaoBookDocument doc = new KakaoBookDocument(
                "Clean Code", List.of("Robert Martin"), "Prentice Hall",
                "https://thumb.jpg", "https://url", "A handbook of agile software craftsmanship", "555", 40000, 35000, "2023-01-01T00:00:00.000+09:00");
        assertThat(BookRecommendService.containsKorean(doc)).isFalse();
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

    // ── buildResponse 신규 필터 ───────────────────────────────────────────

    @Test
    @DisplayName("salePrice=0 도서는 결과에서 제외")
    void buildResponse_zeroPriceBook_excluded() {
        KakaoBookDocument freeBook = new KakaoBookDocument(
                "무료책", List.of("저자"), "출판사",
                "https://thumb.jpg", "https://url", "소개", "777", 20000, 0, "2023-01-01T00:00:00.000+09:00");
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of(freeBook));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).isEmpty();
    }

    @Test
    @DisplayName("한국어 없는 외국 도서는 결과에서 제외")
    void buildResponse_nonKoreanBook_excluded() {
        KakaoBookDocument foreignBook = new KakaoBookDocument(
                "Clean Code", List.of("Robert Martin"), "Prentice Hall",
                "https://thumb.jpg", "https://url", "A handbook of agile software craftsmanship", "888", 40000, 35000, "2023-01-01T00:00:00.000+09:00");
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagRows(new Object[]{"Spring", 5L}, new Object[]{"Java", 4L}, new Object[]{"JPA", 3L}));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of(foreignBook));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).isEmpty();
    }

    @Test
    @DisplayName("키워드 하나당 최대 3권만 수집")
    void buildResponse_perKeywordLimit3() {
        // JPA는 KOREAN_TAG_MAP에 없어 한글 확장 없음 → 검색 1회
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));

        List<KakaoBookDocument> manyBooks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            manyBooks.add(new KakaoBookDocument(
                    "JPA책" + i, List.of("저자"), "출판사",
                    "https://thumb" + i + ".jpg", "https://url", "소개", "jpa" + i, 20000, 18000, "2023-01-01T00:00:00.000+09:00"));
        }
        given(kakaoBookClient.searchBooks(anyString())).willReturn(manyBooks);

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        assertThat(result.books()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("제목 앞부분이 같은 도서는 한 번만 포함")
    void buildResponse_titlePrefixDedup_excludesDuplicate() {
        // 두 책의 공백 제거 10자 prefix가 동일 → 하나만 수집
        KakaoBookDocument book1 = new KakaoBookDocument(
                "자바프로그래밍완전정복 1판", List.of("저자"), "출판사",
                "https://thumb1.jpg", "https://url1", "소개", "aa1", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        KakaoBookDocument book2 = new KakaoBookDocument(
                "자바프로그래밍완전정복 개정판", List.of("저자"), "출판사",
                "https://thumb2.jpg", "https://url2", "소개", "aa2", 20000, 18000, "2023-01-01T00:00:00.000+09:00");
        given(historyRepository.findTagNameCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        Tag tag = Tag.builder().name("JPA").build();
        ReflectionTestUtils.setField(tag, "id", UUID.randomUUID());
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(kakaoBookClient.searchBooks(anyString())).willReturn(List.of(book1, book2));

        BookRecommendResponse result = bookRecommendService.getRecommendBooks(userId);

        long matchCount = result.books().stream()
                .filter(b -> b.title().startsWith("자바프로그래밍완전정복"))
                .count();
        assertThat(matchCount).isEqualTo(1);
    }
}
