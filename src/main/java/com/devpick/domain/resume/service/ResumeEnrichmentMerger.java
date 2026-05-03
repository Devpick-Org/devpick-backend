package com.devpick.domain.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** enrich 패치는 기존 1차 스냅샷과 겹치지 않는 빈 필드에만 적용합니다. */
public final class ResumeEnrichmentMerger {

    private static final int SUMMARY_MIN_CHARS = 40;

    private ResumeEnrichmentMerger() {
    }

    /** baseline 은 merge 직후 ObjectNode 라고 가정합니다. */
    public static JsonNode apply(JsonNode baseline, JsonNode patch) {
        if (!(baseline instanceof ObjectNode mutable)) {
            return baseline;
        }
        mutable = mutable.deepCopy();
        if (patch == null || !patch.isObject()) {
            return mutable;
        }
        ObjectNode patchObj = (ObjectNode) patch;
        if (patchObj.size() == 0) {
            return mutable;
        }

        String sum = mutable.path("summary").asText("").trim();
        if (sum.length() < SUMMARY_MIN_CHARS && patch.hasNonNull("summary")) {
            String p = patch.path("summary").asText("").trim();
            if (!p.isEmpty()) {
                mutable.put("summary", p);
            }
        }

        JsonNode careersB = mutable.path("careers");
        if (careersB.isArray() && careersB.isEmpty()) {
            JsonNode cp = patch.path("careers");
            if (cp.isArray() && !cp.isEmpty()) {
                mutable.set("careers", cp.deepCopy());
            }
        }

        JsonNode projB = mutable.path("projects");
        if (projB.isArray() && projB.isEmpty()) {
            JsonNode pp = patch.path("projects");
            if (pp.isArray() && !pp.isEmpty()) {
                mutable.set("projects", pp.deepCopy());
            }
        }

        JsonNode techB = mutable.path("techStack");
        if (techB.isArray() && techB.isEmpty()) {
            JsonNode tp = patch.path("techStack");
            if (tp.isArray() && !tp.isEmpty()) {
                mutable.set("techStack", tp.deepCopy());
            }
        }

        return mutable;
    }
}
