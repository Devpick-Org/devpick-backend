package com.devpick.domain.job.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobBulletSkillMinerTest {

    @Test
    @DisplayName("우대 불릿에서 TypeScript·테스트 도구·인프라 토큰 추출")
    void mine_koreanPreferredBullets() {
        List<String> bullets = List.of(
                "TypeScript 및 Jest, Mocha 등 테스트 프레임워크 경험",
                "Docker, Kubernetes, Airflow 경험 우대");
        assertThat(JobBulletSkillMiner.mine(bullets))
                .contains("TypeScript", "Jest", "Mocha", "Docker", "Kubernetes", "Airflow");
    }

    @Test
    @DisplayName("자바스크립트 문맥에서는 단독 Java 규칙이 오매칭하지 않음")
    void mine_javaScript_withoutJavaNoise() {
        List<String> bullets = List.of("자바스크립트(ES6+) 능숙하신 분");
        assertThat(JobBulletSkillMiner.mine(bullets)).contains("JavaScript").doesNotContain("Java");
    }
}
