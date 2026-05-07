package com.devpick.domain.job.service;

import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.dto.JobApiModels.SkillGapResponse;
import com.devpick.domain.job.entity.EmploymentType;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.devpick.domain.job.repository.JobBookmarkRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.resume.entity.MasterResume;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobSkillGapServiceTest {

    @InjectMocks private JobService jobService;

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobBookmarkRepository jobBookmarkRepository;
    @Mock private MasterResumeRepository masterResumeRepository;
    @Mock private ResumeCryptoService resumeCryptoService;
    @Mock private UserRepository userRepository;
    @Mock private TagRepository tagRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private JobAiClient jobAiClient;
    @Mock private HistoryRepository historyRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    private static JobPosting postingWith(List<String> requiredSkills, List<String> techStack) {
        JobPosting p = JobPosting.builder()
                .companyName("테스트컴퍼니")
                .title("백엔드 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .jobCategory(JobPostingCategory.BACKEND)
                .experienceLevel(PostingExperienceLevel.JUNIOR)
                .sourceUrl("https://example.com/job/" + UUID.randomUUID())
                .build();
        p.getRequiredSkills().addAll(requiredSkills);
        p.getTechStack().addAll(techStack);
        return p;
    }

    @Test
    @DisplayName("parseStatus=PENDING(requiredSkills 빈 경우) — techStack으로 폴백하여 AI에 missing_skills 전달")
    @SuppressWarnings("unchecked")
    void skillGap_pendingJob_usesTechStackForMissingSkills() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPosting posting = postingWith(List.of(), List.of("Java", "Spring"));

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(posting));
        given(masterResumeRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(jobAiClient.skillGap(any())).willReturn(Map.of("roadmap", List.of("Java 기초 학습")));
        given(tagRepository.findByNameIgnoreCaseIn(any())).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of()));

        SkillGapResponse result = jobService.skillGap(userId, jobId);

        assertThat(result.roadmap()).containsExactly("Java 기초 학습");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobAiClient).skillGap(captor.capture());
        List<String> sentMissingSkills = (List<String>) captor.getValue().get("missing_skills");
        assertThat(sentMissingSkills).containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @DisplayName("사용자가 필수 스킬 모두 보유 — missing=[] 시 techStack 기준으로 콘텐츠 추천")
    @SuppressWarnings("unchecked")
    void skillGap_allSkillsMet_recommendsContentByTechStack() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPosting posting = postingWith(List.of("Java"), List.of("Java"));

        MasterResume resume = MasterResume.builder()
                .userId(userId)
                .encryptedPayload("enc")
                .build();

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(posting));
        given(masterResumeRepository.findByUserId(userId)).willReturn(Optional.of(resume));
        given(resumeCryptoService.decrypt("enc")).willReturn("{\"techStack\":[\"Java\"]}");
        given(jobAiClient.skillGap(any())).willReturn(Map.of("roadmap", List.of()));
        given(tagRepository.findByNameIgnoreCaseIn(any())).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of()));

        jobService.skillGap(userId, jobId);

        // missing=[] 이므로 techStack ["Java"]로 tagRepository 호출돼야 함 (빈 배열로 early return 안 함)
        ArgumentCaptor<List<String>> tagCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagRepository).findByNameIgnoreCaseIn(tagCaptor.capture());
        assertThat(tagCaptor.getValue()).containsExactly("Java");
    }

    @Test
    @DisplayName("일부 스킬 미보유 — missing 스킬 기준으로 AI 호출 및 콘텐츠 추천")
    @SuppressWarnings("unchecked")
    void skillGap_partialMissing_usesMissingSkillsForAiAndContent() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPosting posting = postingWith(List.of("Java", "React"), List.of("Java", "React"));

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(posting));
        given(masterResumeRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(jobAiClient.skillGap(any())).willReturn(Map.of("roadmap", List.of("React 학습")));
        given(tagRepository.findByNameIgnoreCaseIn(any())).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of()));

        SkillGapResponse result = jobService.skillGap(userId, jobId);

        assertThat(result.roadmap()).containsExactly("React 학습");

        ArgumentCaptor<Map<String, Object>> aiCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jobAiClient).skillGap(aiCaptor.capture());
        List<String> sentMissing = (List<String>) aiCaptor.getValue().get("missing_skills");
        assertThat(sentMissing).containsExactlyInAnyOrder("Java", "React");

        ArgumentCaptor<List<String>> tagCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagRepository).findByNameIgnoreCaseIn(tagCaptor.capture());
        assertThat(tagCaptor.getValue()).containsExactlyInAnyOrder("Java", "React");
    }

    @Test
    @DisplayName("requiredSkills·techStack 모두 빈 경우 — 콘텐츠 빈 배열 반환")
    void skillGap_emptyRequiredAndEmptyTechStack_returnsEmptyContent() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPosting posting = postingWith(List.of(), List.of());

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(posting));
        given(masterResumeRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(jobAiClient.skillGap(any())).willReturn(Map.of("roadmap", List.of()));

        SkillGapResponse result = jobService.skillGap(userId, jobId);

        assertThat(result.contents()).isEmpty();
        assertThat(result.roadmap()).isEmpty();
    }
}
