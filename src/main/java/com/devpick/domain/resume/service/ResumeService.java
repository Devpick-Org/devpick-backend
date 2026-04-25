package com.devpick.domain.resume.service;

import com.devpick.domain.resume.entity.MasterResume;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final int MAX_RESUME_JSON_CHARS = 500_000;

    private final MasterResumeRepository masterResumeRepository;
    private final ResumeCryptoService resumeCryptoService;
    private final ObjectMapper objectMapper;

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

    @Transactional
    public void deleteMaster(UUID userId) {
        masterResumeRepository.deleteByUserId(userId);
    }
}
