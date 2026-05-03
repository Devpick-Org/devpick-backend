package com.devpick.domain.resume.service;

import com.devpick.domain.resume.client.ResumeAiClient;
import com.devpick.domain.resume.entity.MasterResume;
import com.devpick.domain.resume.extract.ResumeDocumentTextExtractor;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final int MAX_RESUME_JSON_CHARS = 500_000;
    private static final long MAX_RESUME_UPLOAD_BYTES = 10 * 1024 * 1024L;

    private final MasterResumeRepository masterResumeRepository;
    private final ResumeCryptoService resumeCryptoService;
    private final ObjectMapper objectMapper;
    private final ResumeDocumentTextExtractor resumeDocumentTextExtractor;
    private final ResumeAiClient resumeAiClient;

    @Value("${app.resume.import.enrichment-enabled:false}")
    private boolean resumeImportEnrichmentEnabled;

    @Transactional(readOnly = true)
    public JsonNode getMaster(UUID userId) {
        MasterResume m = masterResumeRepository.findByUserId(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.RESUME_NOT_FOUND));
        try {
            String json = resumeCryptoService.decrypt(m.getEncryptedPayload());
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new DevpickException(ErrorCode.RESUME_NOT_FOUND);
        }
    }

    @Transactional
    public JsonNode upsertMaster(UUID userId, JsonNode body) {
        if (body == null) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        String raw;
        try {
            raw = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        if (raw.length() > MAX_RESUME_JSON_CHARS) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        String sealed = resumeCryptoService.encrypt(raw);
        MasterResume m = masterResumeRepository.findByUserId(userId)
                .orElseGet(() -> MasterResume.builder()
                        .userId(userId)
                        .encryptedPayload("")
                        .build());
        m.setEncryptedPayload(sealed);
        masterResumeRepository.save(m);
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new DevpickException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** PDF 또는 DOCX를 업로드하면 텍스트를 추출하고 AI로 마스터 이력서 JSON으로 변환 후 저장합니다. */
    @Transactional
    public ResumeImportOutcome importMasterFromFile(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_RESUME_UPLOAD_BYTES) {
            throw new DevpickException(ErrorCode.FILE_UPLOAD_TOO_LARGE);
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        ResumeDocumentTextExtractor.Kind kind = resumeDocumentTextExtractor.detectKind(originalName);
        if (kind == null) {
            throw new DevpickException(ErrorCode.FILE_UPLOAD_INVALID_TYPE);
        }
        final byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        final String text;
        try {
            text = resumeDocumentTextExtractor.extract(kind, rawBytes);
        } catch (IOException e) {
            throw new DevpickException(ErrorCode.RESUME_DOCUMENT_TEXT_EMPTY);
        }
        if (text == null || text.strip().length() < 20) {
            throw new DevpickException(ErrorCode.RESUME_DOCUMENT_TEXT_EMPTY);
        }
        final String stripped = text.strip();
        JsonNode ai = resumeAiClient.parseResumeFromText(originalName, stripped, null);
        String iso = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        JsonNode merged = ResumeImportNormalizer.merge(ai, originalName, iso);

        ResumeImportEnrichmentTrigger.Decision decision = ResumeImportEnrichmentTrigger.evaluate(
                resumeImportEnrichmentEnabled, stripped, merged);

        final String enrichmentHeader;
        final JsonNode toSave;

        if (decision == ResumeImportEnrichmentTrigger.Decision.ENRICH) {
            JsonNode mutable = merged;
            String header = ResumeEnrichmentHeader.SKIPPED_ERROR;
            try {
                JsonNode patch = resumeAiClient.enrichResumeFromText(originalName, stripped, merged);
                mutable = ResumeEnrichmentMerger.apply(merged, patch);
                mutable = ResumeImportNormalizer.sanitizeCareersProjects(mutable);
                header = ResumeEnrichmentHeader.APPLIED;
            } catch (Exception e) {
                log.warn("resume enrich failed, using pass1 only: {}", e.toString());
            }
            enrichmentHeader = header;
            toSave = mutable;
        } else {
            enrichmentHeader = switch (decision) {
                case SKIPPED_DISABLED -> ResumeEnrichmentHeader.SKIPPED_DISABLED;
                case SKIPPED_SHORT_TEXT -> ResumeEnrichmentHeader.SKIPPED_SHORT_TEXT;
                case SKIPPED_NO_NEED, SKIPPED_NO_GAPS -> ResumeEnrichmentHeader.SKIPPED_NO_NEED;
                case ENRICH -> ResumeEnrichmentHeader.SKIPPED_ERROR;
            };
            toSave = merged;
        }

        JsonNode saved = upsertMaster(userId, toSave);

        log.info("resume_import enrichment={} textLen={}", enrichmentHeader, stripped.length());

        return new ResumeImportOutcome(saved, enrichmentHeader);
    }

    @Transactional
    public void deleteMaster(UUID userId) {
        masterResumeRepository.deleteByUserId(userId);
    }
}
