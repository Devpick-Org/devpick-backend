package com.devpick.domain.job.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * JD 불릿(특히 우대 조건) 텍스트에서 알려진 기술 키워드를 찾아 정규 표기 스킬 이름으로 반환합니다.
 * AI 파싱이 {@code preferred_skills}를 비우고 본문 문장만 넣은 경우 매칭 UI를 보완하기 위함입니다.
 */
public final class JobBulletSkillMiner {

    private JobBulletSkillMiner() {
    }

    private record Rule(String canonical, Pattern pattern) {}

    /**
     * 앞쪽 규칙이 더 구체적(긴 이름·복합 스택)일수록 우선합니다. 같은 텍스트에 여러 규칙이 맞으면
     * 리스트 순서대로 한 번만 담습니다({@link LinkedHashSet}).
     */
    private static final List<Rule> RULES = List.of(
            new Rule("Spring Boot", Pattern.compile("(?i)spring\\s*boot")),
            new Rule("Spring", Pattern.compile("(?i)\\bspring\\b(?!\\s*boot)")),
            new Rule("Next.js", Pattern.compile("(?i)next\\.js|\\bnextjs\\b")),
            new Rule("Node.js", Pattern.compile("(?i)node\\.js|nodejs|\\bnode\\b")),
            new Rule("Vue.js", Pattern.compile("(?i)vue\\.js|\\bvue\\s*\\.?js\\b|\\bvue\\b")),
            new Rule("React", Pattern.compile("(?i)\\breact\\b|리액트")),
            new Rule("Angular", Pattern.compile("(?i)\\bangular\\b")),
            new Rule("TypeScript", Pattern.compile("(?i)typescript|\\bts\\b|타입스크립트")),
            new Rule("JavaScript", Pattern.compile("(?i)javascript|\\bjs\\b|자바스크립트|ecmascript|\\bes6\\b")),
            new Rule("Express", Pattern.compile("(?i)express\\.js|\\bexpress\\b")),
            new Rule("NestJS", Pattern.compile("(?i)nestjs|nest\\.js")),
            new Rule("FastAPI", Pattern.compile("(?i)fastapi|fast\\s*api")),
            new Rule("Django", Pattern.compile("(?i)\\bdjango\\b")),
            new Rule("Flask", Pattern.compile("(?i)\\bflask\\b")),
            new Rule("GraphQL", Pattern.compile("(?i)graphql|그래프\\s*ql")),
            new Rule("gRPC", Pattern.compile("(?i)\\bgrpc\\b")),
            new Rule("Kubernetes", Pattern.compile("(?i)kubernetes|\\bk8s\\b|쿠버네티스")),
            new Rule("Docker", Pattern.compile("(?i)\\bdocker\\b|도커")),
            new Rule("Terraform", Pattern.compile("(?i)terraform|\\btf\\b")),
            new Rule("Ansible", Pattern.compile("(?i)\\bansible\\b")),
            new Rule("Jenkins", Pattern.compile("(?i)\\bjenkins\\b")),
            new Rule("GitHub Actions", Pattern.compile("(?i)github\\s*actions")),
            new Rule("GitLab CI", Pattern.compile("(?i)gitlab\\s*ci")),
            new Rule("Airflow", Pattern.compile("(?i)airflow|\\bapache\\s*airflow\\b")),
            new Rule("Kafka", Pattern.compile("(?i)\\bkafka\\b|카프카")),
            new Rule("RabbitMQ", Pattern.compile("(?i)rabbitmq|rabbit\\s*mq")),
            new Rule("Redis", Pattern.compile("(?i)\\bredis\\b")),
            new Rule("Elasticsearch", Pattern.compile("(?i)elasticsearch|\\belk\\b")),
            new Rule("MongoDB", Pattern.compile("(?i)mongodb|\\bmongo\\b")),
            new Rule("PostgreSQL", Pattern.compile("(?i)postgresql|\\bpostgres\\b")),
            new Rule("MySQL", Pattern.compile("(?i)\\bmysql\\b")),
            new Rule("AWS", Pattern.compile("(?i)\\baws\\b|아마존\\s*웹\\s*서비스")),
            new Rule("GCP", Pattern.compile("(?i)\\bgcp\\b|google\\s*cloud")),
            new Rule("Azure", Pattern.compile("(?i)\\bazure\\b")),
            new Rule("JUnit", Pattern.compile("(?i)\\bjunit\\b")),
            new Rule("Jest", Pattern.compile("(?i)\\bjest\\b")),
            new Rule("Mocha", Pattern.compile("(?i)\\bmocha\\b")),
            new Rule("Jasmine", Pattern.compile("(?i)\\bjasmine\\b")),
            new Rule("Pytest", Pattern.compile("(?i)pytest|\\bpytest\\b")),
            new Rule("Cypress", Pattern.compile("(?i)\\bcypress\\b")),
            new Rule("Playwright", Pattern.compile("(?i)playwright|플레이라이트")),
            new Rule("Svelte", Pattern.compile("(?i)\\bsvelte\\b")),
            new Rule("Go", Pattern.compile("(?i)\\bgolang\\b|\\bgo\\s*언어\\b|고랭")),
            new Rule("Rust", Pattern.compile("(?i)\\brust\\b")),
            new Rule("Kotlin", Pattern.compile("(?i)\\bkotlin\\b|코틀린")),
            new Rule("Swift", Pattern.compile("(?i)\\bswift\\b|스위프트")),
            new Rule("Python", Pattern.compile("(?i)\\bpython\\b|파이썬")),
            new Rule("Java", Pattern.compile("(?i)(?<![\\w.])java(?![\\w])|자바(?!스크립트)")),
            new Rule("C#", Pattern.compile("(?i)\\bc#\\b|\\bc\\s*sharp\\b")),
            new Rule("C++", Pattern.compile("(?i)\\bc\\+\\+\\b")),
            new Rule("Linux", Pattern.compile("(?i)\\blinux\\b|리눅스")),
            new Rule("Git", Pattern.compile("(?i)\\bgit\\b|깃\\s*협업|깃\\s*사용")));

    public static List<String> mine(List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return List.of();
        }
        String blob = String.join(
                "\n",
                bullets.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
        if (blob.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Rule r : RULES) {
            if (r.pattern.matcher(blob).find()) {
                out.add(r.canonical);
            }
        }
        return new ArrayList<>(out);
    }
}
