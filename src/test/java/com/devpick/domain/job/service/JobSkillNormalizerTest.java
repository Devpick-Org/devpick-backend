package com.devpick.domain.job.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobSkillNormalizerTest {

    @Test
    void canonicalLower_aliasesCollapse() {
        assertThat(JobSkillNormalizer.canonicalLower("TS")).isEqualTo("typescript");
        assertThat(JobSkillNormalizer.canonicalLower("TypeScript")).isEqualTo("typescript");
        assertThat(JobSkillNormalizer.canonicalLower("nestjs")).isEqualTo("nestjs");
        assertThat(JobSkillNormalizer.canonicalLower("nest.js")).isEqualTo("nestjs");
        assertThat(JobSkillNormalizer.canonicalLower("postgreSQL")).isEqualTo("postgresql");
        assertThat(JobSkillNormalizer.canonicalLower("pgsql")).isEqualTo("postgresql");
        assertThat(JobSkillNormalizer.canonicalLower("k8s")).isEqualTo("kubernetes");
        assertThat(JobSkillNormalizer.canonicalLower("unknown-xyz-brand")).isEqualTo("unknown-xyz-brand");
    }
}
