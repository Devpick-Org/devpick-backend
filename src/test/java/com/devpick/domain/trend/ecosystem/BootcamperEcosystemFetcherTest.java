package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootcamperEcosystemFetcherTest {

    @Test
    @DisplayName("부트캠퍼 썸네일 URL 의 url 파라미터에는 승인된 퍼센트 인코딩 경로가 들어간다")
    void bootcamperOptimizedThumbnail_percentEncodesFullPathForNextImageLoader() {
        String thumb = BootcamperEcosystemFetcher.bootcamperOptimizedThumbnail("/uploads/foo bar.png");
        assertThat(thumb).startsWith("https://bootcamper.co.kr/_next/image?url=");
        assertThat(thumb).contains("w=640&q=75");
        assertThat(thumb).contains("%2Fuploads%2F");
        assertThat(thumb).doesNotContain("?url=/uploads/");
    }

    @Test
    @DisplayName("허용 분류 집합에 웹·앱·클라우드·기획·AI·데이터 가 포함된다")
    void includedBootcampClassifies_containsExpectedUiCategories() {
        assertThat(BootcamperEcosystemFetcher.INCLUDED_BOOTCAMP_CLASSIFIES)
                .containsExactlyInAnyOrder("웹개발", "앱개발", "클라우드/보안", "PM/기획", "AI/ML", "데이터");
    }
}
