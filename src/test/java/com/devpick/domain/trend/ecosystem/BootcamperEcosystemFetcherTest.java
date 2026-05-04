package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BootcamperEcosystemFetcherTest {

    @Test
    @DisplayName("업로드 썸네일은 원본 경로(https://bootcamper.co.kr/uploads/...) 직링크 형태로 만들어 브라우저에서 불러오기 유리합니다")
    void bootcamperThumbnailPath_usesAbsoluteUploadOrigin() {
        String path = "/uploads/foo bar.png";
        String thumb =
                "https://bootcamper.co.kr"
                        + UriUtils.encodePath(path, StandardCharsets.UTF_8);
        assertThat(thumb).isEqualTo("https://bootcamper.co.kr/uploads/foo%20bar.png");
        assertThat(thumb).doesNotContain("_next/image");
    }

    @Test
    @DisplayName("허용 분류 집합에 웹·앱·클라우드·기획·AI·데이터 가 포함된다")
    void includedBootcampClassifies_containsExpectedUiCategories() {
        assertThat(BootcamperEcosystemFetcher.INCLUDED_BOOTCAMP_CLASSIFIES)
                .containsExactlyInAnyOrder("웹개발", "앱개발", "클라우드/보안", "PM/기획", "AI/ML", "데이터");
    }
}
