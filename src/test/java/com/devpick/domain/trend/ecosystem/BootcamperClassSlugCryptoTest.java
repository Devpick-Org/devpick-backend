package com.devpick.domain.trend.ecosystem;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;

class BootcamperClassSlugCryptoTest {

    @Test
    @DisplayName("알려진 CryptoJS ciphertext 는 courseList 의 id 문자열 로 복호된다")
    void decryptKnownSample_plainIsBootcamperNumericId2583() throws GeneralSecurityException {
        /* 사용자 예시 소프트웨어 QA 과정 페이지 슬래그(ciphertext 원문 Base64). */
        String knownBase64Cipher = "U2FsdGVkX18kl9AvMhjSK6GNY6Krq3vTStwdd/DaD54=";
        assertThat(BootcamperClassSlugCrypto.decryptSlugBase64WithoutUriEncoding(knownBase64Cipher))
                .isEqualTo("2583");
    }

    @Test
    @DisplayName("encodePathSlug 결과는 디코드 후 동일 플레인 숫자 id 로 복호된다")
    void encodeThenDecode_plainRoundTripsNumericIdString() throws GeneralSecurityException {
        String segment = BootcamperClassSlugCrypto.encodePathSlug(2583);
        assertThat(segment).isNotNull().isNotEmpty();
        String decodedB64 = UriUtils.decode(segment, StandardCharsets.UTF_8);
        assertThat(BootcamperClassSlugCrypto.decryptSlugBase64WithoutUriEncoding(decodedB64))
                .isEqualTo("2583");
    }
}
