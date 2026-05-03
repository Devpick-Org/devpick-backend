package com.devpick.domain.job.service;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 필수 70% + 우대 30% + 숙련도 가중(매칭된 기술의 평균 숙련도 반영).
 */
public final class JobMatchingCalculator {

    private JobMatchingCalculator() {
    }

    public record MatchResult(
            int matchScore,
            List<String> matchedTags,
            List<String> missingTags,
            double requiredRatio,
            double preferredRatio
    ) {}

    public static MatchResult compute(JobPosting posting, Map<String, Integer> userSkillToProficiency) {
        Map<String, Integer> user = normalizeKeys(userSkillToProficiency);
        List<String> required = posting.getRequiredSkills().stream()
                .map(s -> JobSkillNormalizer.canonicalLower(s != null ? s.trim() : ""))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        List<String> preferred = posting.getPreferredSkills().stream()
                .map(s -> JobSkillNormalizer.canonicalLower(s != null ? s.trim() : ""))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (user.isEmpty()) {
            return new MatchResult(
                    0,
                    List.of(),
                    new ArrayList<>(posting.getRequiredSkills()),
                    0,
                    0);
        }

        RequiredPreferredMatch req = matchList(required, user);
        RequiredPreferredMatch pref = matchList(preferred, user);

        double reqRatio = required.isEmpty() ? 1.0 : (double) req.metCount() / required.size();
        /** 우대 스킬이 하나도 없으면 비율·가중치에서 제외(가짜 100% 매칭 방지) */
        double prefRatio = preferred.isEmpty() ? 0.0 : (double) pref.metCount() / preferred.size();

        /*
         * 베이스 점수: 필수 미정의 공고 레거시 처리(예전과 동일, 70% 무조건 채움) +
         * 우대는 스킬이 등록된 경우에만 30% 가중.
         */
        double baseNumerator =
                (required.isEmpty() ? 0.7 : reqRatio * 0.7) + (preferred.isEmpty() ? 0.0 : prefRatio * 0.3);
        double baseDenominator =
                (required.isEmpty() ? 0.7 : 0.7) + (preferred.isEmpty() ? 0.0 : 0.3);
        double base = baseNumerator / baseDenominator;

        double profNumerator =
                (required.isEmpty()
                        ? 0.7 * req.proficiencyFactor()
                        : req.proficiencyFactor() * 0.7)
                        + (preferred.isEmpty() ? 0.0 : pref.proficiencyFactor() * 0.3);
        double profFactor = baseDenominator == 0 ? 1.0 : profNumerator / baseDenominator;
        int score = (int) Math.round(Math.min(100, base * 100 * (0.65 + 0.35 * profFactor)));

        Set<String> matchedOriginal = new HashSet<>();
        matchedOriginal.addAll(req.matchedDisplay());
        matchedOriginal.addAll(pref.matchedDisplay());

        List<String> missing = new ArrayList<>();
        for (String r : posting.getRequiredSkills()) {
            if (!containsSkill(user, r)) {
                missing.add(r);
            }
        }

        List<String> matchedList = new ArrayList<>(matchedOriginal);
        matchedList.sort(String.CASE_INSENSITIVE_ORDER);

        return new MatchResult(score, matchedList, missing, reqRatio, prefRatio);
    }

    private static boolean containsSkill(Map<String, Integer> userNorm, String skill) {
        if (skill == null || skill.isBlank()) {
            return false;
        }
        String k = JobSkillNormalizer.canonicalLower(skill.trim());
        if (k.isEmpty()) {
            return false;
        }
        if (userNorm.containsKey(k)) {
            return true;
        }
        for (String key : userNorm.keySet()) {
            if (key.contains(k) || k.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private record RequiredPreferredMatch(int metCount, double proficiencyFactor, List<String> matchedDisplay) {}

    private static RequiredPreferredMatch matchList(List<String> normalizedSkills, Map<String, Integer> user) {
        if (normalizedSkills.isEmpty()) {
            return new RequiredPreferredMatch(0, 1.0, List.of());
        }
        int met = 0;
        double profSum = 0;
        List<String> matchedDisplay = new ArrayList<>();
        for (String norm : normalizedSkills) {
            Integer p = findProficiency(user, norm);
            if (p != null) {
                met++;
                profSum += Math.clamp(p / 100.0, 0, 1);
                matchedDisplay.add(displayForm(norm));
            }
        }
        double profFactor = met == 0 ? 0 : profSum / met;
        return new RequiredPreferredMatch(met, profFactor, matchedDisplay);
    }

    private static Integer findProficiency(Map<String, Integer> user, String normalizedSkill) {
        if (user.containsKey(normalizedSkill)) {
            return user.get(normalizedSkill);
        }
        for (Map.Entry<String, Integer> e : user.entrySet()) {
            if (e.getKey().contains(normalizedSkill) || normalizedSkill.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String displayForm(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private static Map<String, Integer> normalizeKeys(Map<String, Integer> raw) {
        Map<String, Integer> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Integer> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = e.getKey().toLowerCase(Locale.ROOT).trim();
            if (k.isEmpty()) {
                continue;
            }
            String canon = JobSkillNormalizer.canonicalLower(k);
            int v = e.getValue() == null ? 50 : Math.clamp(e.getValue(), 0, 100);
            out.merge(canon, v, Math::max);
        }
        return out;
    }

    /**
     * 이력서·태그 등에서 모은 원본 표기(Java, NODE.JS 등)를 소문자 키로 통일합니다.
     * {@link #compute}와 동일한 정규화로 상세 매칭(breakdown)과 목록 스코어가 어긋나지 않게 합니다.
     */
    public static Map<String, Integer> normalizeSkillMap(Map<String, Integer> raw) {
        return normalizeKeys(raw);
    }

    /** resume JSON: techStack: ["Java"] 또는 [{ "name": "Java", "proficiency": 80 }] */
    public static Map<String, Integer> skillsFromResumeJson(JsonNode root) {
        Map<String, Integer> map = new HashMap<>();
        if (root == null || !root.has("techStack")) {
            return map;
        }
        JsonNode ts = root.get("techStack");
        if (ts.isArray()) {
            for (JsonNode n : ts) {
                if (n.isTextual()) {
                    map.put(n.asText(), 60);
                } else if (n.isObject() && n.has("name")) {
                    String name = n.get("name").asText("");
                    int p = n.has("proficiency") ? n.get("proficiency").asInt(60) : 60;
                    map.put(name, p);
                }
            }
        }
        return map;
    }

    /**
     * 공고의 경력 구분은 "이 포지션에 필요한 최소 역량 묶음"의 하한을 나타낸다.
     * 과다 경력(오버 퀄리파이)은 불합격 사유가 아니다.
     */
    public static int experienceScoreMet(PostingExperienceLevel level, int careerYears) {
        if (level == null || level == PostingExperienceLevel.ANY) {
            return 1;
        }
        int y = Math.max(0, careerYears);
        int minYears = postingMinCareerYears(level);
        return y >= minYears ? 1 : 0;
    }

    /** 공고 레벨별 필요 최소 연차 하한 */
    static int postingMinCareerYears(PostingExperienceLevel level) {
        if (level == null || level == PostingExperienceLevel.ANY) {
            return 0;
        }
        return switch (level) {
            case ANY -> 0;
            case NEW -> 0;
            case JUNIOR -> 0;
            case MIDDLE -> 2;
            case SENIOR -> 5;
        };
    }

    public static int careerYearsFromResume(JsonNode root) {
        if (root == null || !root.has("basicInfo")) {
            return 0;
        }
        JsonNode bi = root.get("basicInfo");
        if (bi.has("careerYears")) {
            return bi.get("careerYears").asInt(0);
        }
        return 0;
    }
}
