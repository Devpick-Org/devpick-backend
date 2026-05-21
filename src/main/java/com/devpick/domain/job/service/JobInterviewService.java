package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.entity.JobInterviewQa;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.repository.JobInterviewQaRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.repository.JobPostingSpecifications;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.job.dto.JobApiModels.InterviewQaListItemResponse;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobInterviewService {

    private final JobPostingRepository jobPostingRepository;
    private final JobInterviewQaRepository jobInterviewQaRepository;
    private final MasterResumeRepository masterResumeRepository;
    private final ResumeCryptoService resumeCryptoService;
    private final JobAiClient jobAiClient;
    private final JobService jobService;
    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @Transactional(readOnly = true)
    public List<InterviewQaListItemResponse> listForUser(UUID userId) {
        return jobInterviewQaRepository.findAllByUserIdWithPostingOrderByUpdatedAtDesc(userId).stream()
                .filter(q -> JobPostingSpecifications.passesListableQuality(
                        q.getJobPosting().getTitle(), q.getJobPosting().getCompanyName()))
                .map(q -> {
                    JobPosting job = q.getJobPosting();
                    int score = jobService.computeMatchScoreForJob(userId, job.getId());
                    return new InterviewQaListItemResponse(
                            job.getId().toString(),
                            job.getCompanyName(),
                            job.getTitle(),
                            score,
                            q.getPayloadJson(),
                            q.getUpdatedAt().toString()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public String getPayload(UUID userId, UUID jobId) {
        return jobInterviewQaRepository.findByUserIdAndJobPosting_Id(userId, jobId)
                .map(JobInterviewQa::getPayloadJson)
                .orElseThrow(() -> new DevpickException(ErrorCode.INTERVIEW_QA_NOT_FOUND));
    }

    @Transactional
    public String generateAndSave(UUID userId, UUID jobId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        planLimitService.checkAndIncrementWeekly(userId, user.getPlanType(), "interview_qa_gen");
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        if (!JobPostingSpecifications.passesListableQuality(job.getTitle(), job.getCompanyName())) {
            throw new DevpickException(ErrorCode.JOB_NOT_FOUND);
        }
        var resume = masterResumeRepository.findByUserId(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.RESUME_NOT_FOUND));
        String resumeJson;
        try {
            resumeJson = resumeCryptoService.decrypt(resume.getEncryptedPayload());
        } catch (Exception e) {
            throw new DevpickException(ErrorCode.RESUME_NOT_FOUND);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("job_title", job.getTitle());
        body.put("company_name", job.getCompanyName());
        body.put("required_skills", job.getRequiredSkills());
        body.put("preferred_skills", job.getPreferredSkills());
        body.put("resume_json", resumeJson);

        String payload = jobAiClient.generateInterviewQa(body);

        JobInterviewQa entity = jobInterviewQaRepository.findByUserIdAndJobPosting_Id(userId, jobId)
                .orElseGet(() -> JobInterviewQa.builder()
                        .userId(userId)
                        .jobPosting(job)
                        .payloadJson("")
                        .build());
        entity.setPayloadJson(payload);
        jobInterviewQaRepository.save(entity);
        return payload;
    }

    @Transactional
    public void delete(UUID userId, UUID jobId) {
        if (!jobInterviewQaRepository.findByUserIdAndJobPosting_Id(userId, jobId).isPresent()) {
            throw new DevpickException(ErrorCode.INTERVIEW_QA_NOT_FOUND);
        }
        jobInterviewQaRepository.deleteByUserIdAndJobPosting_Id(userId, jobId);
    }
}
