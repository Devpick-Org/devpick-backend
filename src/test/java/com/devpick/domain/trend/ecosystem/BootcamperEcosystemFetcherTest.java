package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootcamperEcosystemFetcherTest {

    @Test
    @DisplayName("썸네일 파일명만 오면 Next 이미지 로더(/_next/image)에 업로드 경로를 넣습니다 (직링크 uploads 는 404)")
    void bootcamperThumbnail_filenameOnly_usesNextImageLoader() {
        String thumb = BootcamperEcosystemFetcher.bootcamperThumbnailForList("foo bar.png");
        assertThat(thumb).startsWith("https://bootcamper.co.kr/_next/image?url=");
        assertThat(thumb).contains("w=640&q=75");
        assertThat(thumb).contains("%2Fuploads%2F");
        assertThat(thumb).doesNotContain("?url=/uploads/");
    }

    @Test
    @DisplayName("uploads/ 접두(슬래시 없음)도 /uploads/ 로 규격화합니다")
    void normalizedBootcampUploadPath_uploadsPrefixWithoutLeadingSlash() {
        assertThat(BootcamperEcosystemFetcher.normalizedBootcampUploadPath("uploads/a.png")).isEqualTo("/uploads/a.png");
        assertThat(BootcamperEcosystemFetcher.normalizedBootcampUploadPath("/uploads/a.png")).isEqualTo("/uploads/a.png");
    }

    @Test
    @DisplayName("부트캠퍼 업로드 절대 URL 은 pathname 만 사용합니다")
    void normalizedBootcampUploadPath_absoluteBootcamperHttps() {
        assertThat(
                        BootcamperEcosystemFetcher.normalizedBootcampUploadPath(
                                "https://bootcamper.co.kr/uploads/foo%20x.png"))
                .isEqualTo("/uploads/foo x.png");
    }

    @Test
    @DisplayName("다른 호스트 업로드 URL 은 무시합니다")
    void bootcamperThumbnail_foreignHost_returnsNull() {
        assertThat(BootcamperEcosystemFetcher.bootcamperThumbnailForList("https://evil.example/uploads/a.png"))
                .isNull();
    }

    @Test
    @DisplayName("경로 순회 문자열은 거부합니다")
    void normalizedBootcampUploadPath_rejectsDotDot() {
        assertThat(BootcamperEcosystemFetcher.normalizedBootcampUploadPath("/uploads/../etc/passwd")).isEmpty();
    }

    @Test
    @DisplayName("허용 분류 집합에 웹·앱·클라우드·기획·AI·데이터 가 포함된다")
    void includedBootcampClassifies_containsExpectedUiCategories() {
        assertThat(BootcamperEcosystemFetcher.INCLUDED_BOOTCAMP_CLASSIFIES)
                .containsExactlyInAnyOrder("웹개발", "앱개발", "클라우드/보안", "PM/기획", "AI/ML", "데이터");
    }

    @Test
    @DisplayName("부트캠퍼 호스트 판별")
    void bootcamperHost_acceptsWwwStrip() {
        assertThat(BootcamperEcosystemFetcher.bootcamperHost("bootcamper.co.kr")).isTrue();
        assertThat(BootcamperEcosystemFetcher.bootcamperHost("WWW.bootcamper.co.kr")).isTrue();
        assertThat(BootcamperEcosystemFetcher.bootcamperHost("notbootcamper.co.kr")).isFalse();
    }
}
