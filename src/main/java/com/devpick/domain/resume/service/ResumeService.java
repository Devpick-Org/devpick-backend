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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
    public JsonNode importMasterFromFile(UUID userId, MultipartFile file) {
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
        final byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        final String text;
        try {
            text = resumeDocumentTextExtractor.extract(kind, raw);
        } catch (IOException e) {
            throw new DevpickException(ErrorCode.RESUME_DOCUMENT_TEXT_EMPTY);
        }
        if (text == null || text.strip().length() < 20) {
            throw new DevpickException(ErrorCode.RESUME_DOCUMENT_TEXT_EMPTY);
        }
        JsonNode ai = resumeAiClient.parseResumeFromText(originalName, text.strip(), null);
        String iso = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        JsonNode merged = ResumeImportNormalizer.merge(ai, originalName, iso);
        return upsertMaster(userId, merged);
    }

    @Transactional
    public void deleteMaster(UUID userId) {
        masterResumeRepository.deleteByUserId(userId);
    }
}
