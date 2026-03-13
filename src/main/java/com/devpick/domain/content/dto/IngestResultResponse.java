package com.devpick.domain.content.dto;

/**
 * POST /internal/contents 응답 DTO.
 * MVP 기준: saved/skipped 카운트만 반환.
 * 추후 요약 자동 트리거 시점에 contentIds 필드 추가 예정.
 */
public record IngestResultResponse(int saved, int skipped) {
}
