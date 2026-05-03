package com.devpick.domain.resume.service;

import com.fasterxml.jackson.databind.JsonNode;

/** 파일 import 후 저장된 이력서 본문 + 보강 여부 헤더 값 */
public record ResumeImportOutcome(JsonNode resume, String enrichmentHeader) {
}
