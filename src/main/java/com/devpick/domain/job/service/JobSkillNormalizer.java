package com.devpick.domain.job.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 이력서·공고·태그에 흔한 기술 표기 차이(ts/type script, nest/nestjs 등)를 한 키로 묶어
 * 매칭 시 동일 스킬로 취급합니다.
 */
public final class JobSkillNormalizer {

    /** 소문자 별칭·정규표기 → 소문자 정균형(canonical) 키 */
    private static final Map<String, String> SYNONYM_TO_CANONICAL;

    static {
        Map<String, String> m = new HashMap<>();

        add(m, "typescript", "ts");
        add(m, "javascript", "js", "ecmascript");
        add(m, "node.js", "nodejs", "node");
        add(m, "express", "express.js", "expressjs");
        add(m, "nestjs", "nest.js");
        add(m, "react", "reactjs", "react.js");
        add(m, "next.js", "nextjs");
        add(m, "vue.js", "vue", "vuejs");
        add(m, "angular", "angularjs");
        add(m, "svelte", "sveltejs");

        add(m, "spring boot", "springboot");
        add(m, "spring", "spring framework");
        add(m, "java", "openjdk");

        add(m, "kotlin");
        add(m, "python", "py");
        add(m, "go", "golang");
        add(m, "rust");
        add(m, "swift");
        add(m, "c++", "cpp", "cplusplus", "c plus plus");
        add(m, "c#", "csharp", "c-sharp");
        add(m, "ruby", "rb");
        add(m, "php");
        add(m, "scala");
        add(m, "dart");

        add(m, "graphql", "gql");
        add(m, "grpc", "g-rpc");

        add(m, "postgresql", "postgres", "pgsql", "psql");
        add(m, "mysql", "mariadb");
        add(m, "mongodb", "mongo");
        add(m, "redis");
        add(m, "elasticsearch", "elastic", "elastic search");
        add(m, "kafka");
        add(m, "rabbitmq", "rabbit mq");

        add(m, "docker");
        add(m, "kubernetes", "k8s");
        add(m, "terraform", "tf");
        add(m, "ansible");

        add(m, "django");
        add(m, "flask");
        add(m, "fastapi", "fast api");

        add(m, "aws", "amazon web services");
        add(m, "gcp", "google cloud");
        add(m, "azure", "microsoft azure");

        add(m, "jest");
        add(m, "mocha");
        add(m, "jasmine");
        add(m, "cypress");
        add(m, "playwright");
        add(m, "pytest", "py.test");
        add(m, "junit");

        add(m, "git");
        add(m, "github actions", "gh actions");
        add(m, "jenkins");
        add(m, "nginx");
        add(m, "linux", "gnu/linux");

        SYNONYM_TO_CANONICAL = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, String> m, String canonical, String... aliases) {
        String c = canonForm(canonical);
        m.put(c, c);
        for (String a : aliases) {
            String key = canonForm(a);
            if (!key.isEmpty()) {
                m.put(key, c);
            }
        }
    }

    private static String canonForm(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * @return 빈 문자열이면 빈 문자열 · 그 외 소문자 canonical 토큰(공백 포함 복합어는 그대로 유지 가능)
     */
    public static String canonicalLower(String skill) {
        if (skill == null || skill.isBlank()) {
            return "";
        }
        String k = skill.toLowerCase(Locale.ROOT).trim();
        return SYNONYM_TO_CANONICAL.getOrDefault(k, k);
    }

    private JobSkillNormalizer() {
    }
}
