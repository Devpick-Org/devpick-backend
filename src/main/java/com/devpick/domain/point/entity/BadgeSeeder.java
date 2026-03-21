package com.devpick.domain.point.entity;

import com.devpick.domain.point.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서버 시작 시 배지 정의 데이터를 seed 삽입한다 (DP-269).
 * 이미 존재하는 배지는 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class BadgeSeeder implements ApplicationRunner {

    private final BadgeRepository badgeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Badge> seeds = List.of(
                Badge.builder().id("FIRST_SCRAP").name("첫 스크랩").description("스크랩 1회 달성").sortOrder(1).build(),
                Badge.builder().id("FIRST_QUESTION").name("첫 질문").description("질문 작성 1회 달성").sortOrder(2).build(),
                Badge.builder().id("ANSWER_MASTER").name("답변 고수").description("답변 채택 5회 달성").sortOrder(3).build(),
                Badge.builder().id("POINT_100").name("새싹 개발자").description("누적 포인트 100p 달성").sortOrder(4).build(),
                Badge.builder().id("POINT_500").name("성장 중").description("누적 포인트 500p 달성").sortOrder(5).build(),
                Badge.builder().id("POINT_1000").name("시니어 픽커").description("누적 포인트 1000p 달성").sortOrder(6).build(),
                Badge.builder().id("STREAK_7").name("7일 연속").description("연속 로그인 7일 달성").sortOrder(7).build()
        );

        for (Badge seed : seeds) {
            if (!badgeRepository.existsById(seed.getId())) {
                badgeRepository.save(seed);
            }
        }
    }
}