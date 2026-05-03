package com.devpick.domain.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * AI 결과를 마스터 이력서 JSON 스키마에 맞게 보정합니다(누락 필드 채우기 등).
 */
public final class ResumeImportNormalizer {

    private ResumeImportNormalizer() {
    }

    public static JsonNode merge(JsonNode ai, String fallbackFileName, String uploadedIso) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode root = f.objectNode();

        String fnFromAi = textOr(ai, "fileName").trim();
        root.put("fileName", fnFromAi.isEmpty() ? sanitizeFileName(fallbackFileName) : fnFromAi);
        root.put("uploadedAt", uploadedIso);

        ObjectNode bi = f.objectNode();
        JsonNode abi = ai.path("basicInfo");
        bi.put("name", textOr(abi, "name"));
        bi.put("jobTitle", textOr(abi, "jobTitle"));
        bi.put("careerYears", intOrZero(abi, "careerYears"));
        bi.put("location", textOr(abi, "location"));
        root.set("basicInfo", bi);

        root.put("summary", textOr(ai, "summary"));

        ArrayNode tech = f.arrayNode();
        if (ai.has("techStack") && ai.get("techStack").isArray()) {
            for (JsonNode t : ai.get("techStack")) {
                String s = t.asText("");
                if (!s.trim().isEmpty()) {
                    tech.add(s.trim());
                }
            }
        }
        root.set("techStack", tech);

        root.set("careers", careersArray(f, ai.path("careers")));
        root.set("projects", projectsArray(f, ai.path("projects")));

        return root;
    }

    /**
     * enrich 적용 후 careers/projects 재필터(빈 행 제거) — 동일 규칙으로 정리합니다.
     */
    public static JsonNode sanitizeCareersProjects(JsonNode root) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (!(root instanceof ObjectNode obj)) {
            return root;
        }
        ObjectNode copy = obj.deepCopy();
        copy.set("careers", careersArray(f, copy.path("careers")));
        copy.set("projects", projectsArray(f, copy.path("projects")));
        return copy;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().replace('\\', '_').replace('/', '_');
    }

    private static String textOr(JsonNode parent, String field) {
        if (parent.hasNonNull(field)) {
            return parent.get(field).asText("");
        }
        String snake = toSnakeCase(field);
        if (snake != null && parent.hasNonNull(snake)) {
            return parent.get(snake).asText("");
        }
        return "";
    }

    private static int intOrZero(JsonNode parent, String field) {
        if (!parent.has(field)) {
            String snake = toSnakeCase(field);
            if (snake != null && parent.has(snake)) {
                int v = parent.get(snake).asInt(-1);
                return Math.max(0, v);
            }
            return 0;
        }
        JsonNode n = parent.get(field);
        if (n.canConvertToInt()) {
            int v = n.asInt(0);
            return Math.max(0, v);
        }
        if (n.isTextual()) {
            try {
                return Math.max(0, Integer.parseInt(n.asText().replaceAll("\\D+", "")));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static ArrayNode careersArray(JsonNodeFactory f, JsonNode arr) {
        ArrayNode out = f.arrayNode();
        if (!arr.isArray()) {
            return out;
        }
        for (JsonNode c : arr) {
            if (!c.isObject()) {
                continue;
            }
            ObjectNode row = f.objectNode();
            row.put("company", textOr(c, "company"));
            row.put("role", textOr(c, "role"));
            row.put("period", textOr(c, "period"));
            row.put("description", textOr(c, "description"));
            String company = row.get("company").asText("");
            String period = row.get("period").asText("");
            String desc = row.get("description").asText("");
            if (!company.isBlank() || !period.isBlank() || !desc.isBlank()
                    || !row.get("role").asText("").isBlank()) {
                out.add(row);
            }
        }
        return out;
    }

    private static ArrayNode projectsArray(JsonNodeFactory f, JsonNode arr) {
        ArrayNode out = f.arrayNode();
        if (!arr.isArray()) {
            return out;
        }
        for (JsonNode p : arr) {
            if (!p.isObject()) {
                continue;
            }
            ObjectNode row = f.objectNode();
            row.put("name", textOr(p, "name"));
            row.put("period", textOr(p, "period"));
            row.put("role", textOr(p, "role"));
            row.put("description", textOr(p, "description"));
            row.put("achievements", textOr(p, "achievements"));

            ArrayNode ts = f.arrayNode();
            if (p.has("techStack") && p.get("techStack").isArray()) {
                for (JsonNode t : p.get("techStack")) {
                    String s = t.asText("");
                    if (!s.trim().isEmpty()) {
                        ts.add(s.trim());
                    }
                }
            }
            row.set("techStack", ts);

            String name = row.get("name").asText("");
            if (!name.trim().isEmpty() || ts.size() > 0
                    || !row.get("description").asText("").trim().isEmpty()) {
                out.add(row);
            }
        }
        return out;
    }

    /** jobTitle ↔ job_title 등 허술한 응답을 수용하기 위한 선택적 매핑. */
    private static String toSnakeCase(String camel) {
        return switch (camel) {
            case "jobTitle" -> "job_title";
            case "careerYears" -> "career_years";
            default -> null;
        };
    }
}
