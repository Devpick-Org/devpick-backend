package com.devpick.domain.trend.ecosystem;

import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.util.UriUtils;

/**
 * 부트캠퍼 상세 경로 슬래그 생성. 클라이언트와 동일하게 OpenSSL 형식 salted AES-256-CBC 를 쓴다
 * ({@code AES.encrypt(String(id), "bootcamper-secret-key")} 결과 Base64 문자열 후 path 세그먼트 인코딩).
 *
 * @see <a href="https://stackoverflow.com/questions/41432896">CryptoJS ↔ Java 호환 참고(Evp BytesToKey)</a>
 */
final class BootcamperClassSlugCrypto {

    /** 공개 번들({@code preProcessParams}) 과 동일. */
    static final byte[] PASSPHRASE_UTF8 =
            "bootcamper-secret-key".getBytes(StandardCharsets.UTF_8);

    private static final byte[] SALTED_PREFIX =
            "Salted__".getBytes(StandardCharsets.US_ASCII);

    private static final SecureRandom RNG = new SecureRandom();

    private BootcamperClassSlugCrypto() {}

    /**
     * {@code /class/{slug}} 용 세그먼트: Base64 ciphertext 를 path 세그먼트 규칙으로 인코딩합니다.
     */
    static String encodePathSlug(int bootcamperNumericId) {
        if (bootcamperNumericId <= 0) {
            return null;
        }
        try {
            String base64Cipher = opensslEncryptSaltedBase64Utf8Plain(
                    Integer.toString(bootcamperNumericId));
            return UriUtils.encodePathSegment(base64Cipher, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /**
     * 알려진 OpenSSL ciphertext (Base64) 복호 — 테스트 검증 및 라운드트립용.
     */
    static String decryptSlugBase64WithoutUriEncoding(String opensslBase64CiphertextUtf8Ascii)
            throws GeneralSecurityException {
        return new String(openSslDecryptToBytes(opensslBase64CiphertextUtf8Ascii), StandardCharsets.UTF_8);
    }

    private static String opensslEncryptSaltedBase64Utf8Plain(String plainAscii)
            throws GeneralSecurityException {
        byte[] salt = new byte[8];
        RNG.nextBytes(salt);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[][] keyIv = opensslEvpBytesToKey(32, 16, 1, salt, PASSPHRASE_UTF8, md5);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyIv[0], "AES"),
                new IvParameterSpec(keyIv[1]));
        byte[] ciphertext = cipher.doFinal(plainAscii.getBytes(StandardCharsets.UTF_8));
        byte[] blob = new byte[SALTED_PREFIX.length + salt.length + ciphertext.length];
        System.arraycopy(SALTED_PREFIX, 0, blob, 0, SALTED_PREFIX.length);
        System.arraycopy(salt, 0, blob, SALTED_PREFIX.length, salt.length);
        System.arraycopy(
                ciphertext, 0, blob, SALTED_PREFIX.length + salt.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(blob);
    }

    private static byte[] openSslDecryptToBytes(String opensslBase64) throws GeneralSecurityException {
        byte[] all = Base64.getDecoder().decode(opensslBase64.trim());
        if (all.length < 16) {
            throw new GeneralSecurityException("Cipher blob too short");
        }
        for (int i = 0; i < SALTED_PREFIX.length; i++) {
            if (all[i] != SALTED_PREFIX[i]) {
                throw new GeneralSecurityException("Not OpenSSL salted format");
            }
        }
        byte[] salt = Arrays.copyOfRange(all, 8, 16);
        byte[] ciphertext = Arrays.copyOfRange(all, 16, all.length);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[][] keyIv = opensslEvpBytesToKey(32, 16, 1, salt, PASSPHRASE_UTF8, md5);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyIv[0], "AES"), new IvParameterSpec(keyIv[1]));
        return c.doFinal(ciphertext);
    }

    /**
     * OpenSSL {@code EVP_BytesToKey} (MD5, iter 1): CryptoJS 기본과 호환됩니다.
     */
    private static byte[][] opensslEvpBytesToKey(
            int keyLength,
            int ivLength,
            int iterations,
            byte[] salt,
            byte[] password,
            MessageDigest md)
            throws GeneralSecurityException {
        int digestLength = md.getDigestLength();
        int requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength;
        byte[] generatedData = new byte[requiredLength];
        int generatedLength = 0;

        try {
            md.reset();
            while (generatedLength < keyLength + ivLength) {
                if (generatedLength > 0) {
                    md.update(generatedData, generatedLength - digestLength, digestLength);
                }
                md.update(password);
                if (salt != null) {
                    md.update(salt, 0, 8);
                }
                md.digest(generatedData, generatedLength, digestLength);

                for (int i = 1; i < iterations; i++) {
                    md.reset();
                    md.update(generatedData, generatedLength, digestLength);
                    md.digest(generatedData, generatedLength, digestLength);
                }
                generatedLength += digestLength;
            }
            byte[][] result = new byte[2][];
            result[0] = Arrays.copyOfRange(generatedData, 0, keyLength);
            result[1] = Arrays.copyOfRange(generatedData, keyLength, keyLength + ivLength);
            return result;
        } catch (DigestException e) {
            throw new GeneralSecurityException(e);
        } finally {
            Arrays.fill(generatedData, (byte) 0);
        }
    }
}
