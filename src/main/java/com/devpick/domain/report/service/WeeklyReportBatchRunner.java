package com.devpick.domain.report.service;

import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 주간 리포트 생성을 호출마다 별도 트랜잭션으로 커밋합니다 (백필·장시간 배치 시 한 주 실패가 전체를 망가뜨리지 않도록).
 */
@Service
public class WeeklyReportBatchRunner {

    private final WeeklyReportService weeklyReportService;
    private final UserRepository userRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    public WeeklyReportBatchRunner(
            @Lazy WeeklyReportService weeklyReportService,
            UserRepository userRepository,
            WeeklyReportRepository weeklyReportRepository) {
        this.weeklyReportService = weeklyReportService;
        this.userRepository = userRepository;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    /**
     * @return 신규 생성이면 1, 이미 있거나 스킵이면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createReportIfAbsent(UUID userId, LocalDate weekStart) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null) {
            return 0;
        }
        if (weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)) {
            return 0;
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        weeklyReportService.createWeeklyReportForUser(user, weekStart, weekEnd);
        return 1;
    }
}
