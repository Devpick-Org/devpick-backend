package com.devpick.domain.job.schedule;

import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingExpireScheduler {

    private final JobPostingRepository jobPostingRepository;

    /** 매일 00:00 (서버 타임존) — 마감일이 지난 활성 공고를 EXPIRED 로 표시 */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireOutdatedPostings() {
        int n = jobPostingRepository.expireActiveBefore(
                JobPostingStatus.EXPIRED, JobPostingStatus.ACTIVE, LocalDate.now());
        if (n > 0) {
            log.info("Job postings expired by deadline: {}", n);
        }
    }
}
