package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.JobApiModels.JobBookmarkListResponse;
import com.devpick.domain.job.entity.EmploymentType;
import com.devpick.domain.job.entity.JobBookmark;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.devpick.domain.job.repository.JobBookmarkRepository;
import com.devpick.global.entity.BaseTimeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JobBookmarkServiceTest {

    @InjectMocks private JobService jobService;

    @Mock private JobBookmarkRepository jobBookmarkRepository;
    @Mock private com.devpick.domain.job.repository.JobPostingRepository jobPostingRepository;
    @Mock private com.devpick.domain.resume.repository.MasterResumeRepository masterResumeRepository;
    @Mock private com.devpick.domain.resume.service.ResumeCryptoService resumeCryptoService;
    @Mock private com.devpick.domain.user.repository.UserRepository userRepository;
    @Mock private com.devpick.domain.user.repository.TagRepository tagRepository;
    @Mock private com.devpick.domain.content.repository.ContentRepository contentRepository;
    @Mock private com.devpick.domain.job.client.JobAiClient jobAiClient;

    private static void setCreatedAt(Object entity, LocalDateTime value) throws Exception {
        Field field = BaseTimeEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, value);
    }

    @Test
    @DisplayName("북마크 목록 조회 - 북마크가 있을 때 정상 반환")
    void getBookmarkedJobs_withBookmarks_returnsItems() throws Exception {
        UUID userId = UUID.randomUUID();
        JobPosting posting = JobPosting.builder()
                .companyName("카카오")
                .title("백엔드 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(PostingExperienceLevel.JUNIOR)
                .build();
        JobBookmark bookmark = JobBookmark.builder()
                .userId(userId)
                .jobPosting(posting)
                .build();
        setCreatedAt(bookmark, LocalDateTime.now());

        given(jobBookmarkRepository.findByUserIdWithPosting(eq(userId), any()))
                .willReturn(new PageImpl<>(List.of(bookmark)));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, null, PageRequest.of(0, 20));

        assertThat(result.bookmarks()).hasSize(1);
        assertThat(result.bookmarks().get(0).companyName()).isEqualTo("카카오");
        assertThat(result.bookmarks().get(0).matchScore()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("북마크 목록 조회 - 북마크 없을 때 빈 배열 반환")
    void getBookmarkedJobs_noBookmarks_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        given(jobBookmarkRepository.findByUserIdWithPosting(eq(userId), any()))
                .willReturn(new PageImpl<>(List.of()));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, null, PageRequest.of(0, 20));

        assertThat(result.bookmarks()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("북마크 목록 조회 - 검색어 전달 시 검색 쿼리 호출됨")
    void getBookmarkedJobs_withQuery_callsSearchRepository() {
        UUID userId = UUID.randomUUID();
        given(jobBookmarkRepository.findByUserIdWithPostingAndSearch(eq(userId), eq("카카오"), any()))
                .willReturn(new PageImpl<>(List.of()));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, "카카오", PageRequest.of(0, 20));

        assertThat(result.bookmarks()).isEmpty();
    }

    @Test
    @DisplayName("북마크 목록 조회 - 로고/위치가 있을 때 그대로 반환")
    void getBookmarkedJobs_withLogoAndLocation_returnsValues() throws Exception {
        UUID userId = UUID.randomUUID();
        JobPosting posting = JobPosting.builder()
                .companyName("네이버")
                .companyLogoUrl("https://logo.naver.com/logo.png")
                .title("프론트엔드 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(PostingExperienceLevel.SENIOR)
                .location("경기 성남시")
                .build();
        JobBookmark bookmark = JobBookmark.builder()
                .userId(userId)
                .jobPosting(posting)
                .build();
        setCreatedAt(bookmark, LocalDateTime.now());

        given(jobBookmarkRepository.findByUserIdWithPosting(eq(userId), any()))
                .willReturn(new PageImpl<>(List.of(bookmark)));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, null, PageRequest.of(0, 20));

        assertThat(result.bookmarks().get(0).companyLogo()).isEqualTo("https://logo.naver.com/logo.png");
        assertThat(result.bookmarks().get(0).location()).isEqualTo("경기 성남시");
    }

    @Test
    @DisplayName("북마크 목록 조회 - matchScore가 0 이상으로 반환됨")
    void getBookmarkedJobs_returnsMatchScore() throws Exception {
        UUID userId = UUID.randomUUID();
        JobPosting posting = JobPosting.builder()
                .companyName("라인")
                .title("백엔드 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(PostingExperienceLevel.JUNIOR)
                .build();
        JobBookmark bookmark = JobBookmark.builder()
                .userId(userId)
                .jobPosting(posting)
                .build();
        setCreatedAt(bookmark, LocalDateTime.now());

        given(jobBookmarkRepository.findByUserIdWithPosting(eq(userId), any()))
                .willReturn(new PageImpl<>(List.of(bookmark)));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, null, PageRequest.of(0, 20));

        assertThat(result.bookmarks().get(0).matchScore()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("북마크 목록 조회 - 상시 채용 공고는 deadline이 '채용 시 마감'으로 반환됨")
    void getBookmarkedJobs_rollingDeadline_returnsLabel() throws Exception {
        UUID userId = UUID.randomUUID();
        JobPosting posting = JobPosting.builder()
                .companyName("토스")
                .title("iOS 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(PostingExperienceLevel.MIDDLE)
                .rollingDeadline(true)
                .build();
        JobBookmark bookmark = JobBookmark.builder()
                .userId(userId)
                .jobPosting(posting)
                .build();
        setCreatedAt(bookmark, LocalDateTime.now());

        given(jobBookmarkRepository.findByUserIdWithPosting(eq(userId), any()))
                .willReturn(new PageImpl<>(List.of(bookmark)));

        JobBookmarkListResponse result = jobService.getBookmarkedJobs(userId, null, PageRequest.of(0, 20));

        assertThat(result.bookmarks().get(0).deadline()).isEqualTo("채용 시 마감");
    }
}
