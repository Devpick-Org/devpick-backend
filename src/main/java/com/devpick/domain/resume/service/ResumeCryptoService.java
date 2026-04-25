package com.devpick.domain.resume.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 마스터 이력서 JSON 암복호화 (AES-256-GCM).
 * {@code app.resume.encryption-key} 가 비어 있으면 평문 저장(로컬 편의).
 */
@Service
public class ResumeCryptoService {

    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final boolean encryptionEnabled;

    public ResumeCryptoService(@Value("${app.resume.encryption-key:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            this.secretKey = null;
            this.encryptionEnabled = false;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.resume.encryption-key must decode to 32 bytes (AES-256)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, AES);
        this.encryptionEnabled = true;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        if (!encryptionEnabled) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("이력서 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!encryptionEnabled) {
            return stored;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stored);
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] cipherBytes = new byte[buf.remaining()];
            buf.get(cipherBytes);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("이력서 복호화 실패", e);
        }
    }
}
