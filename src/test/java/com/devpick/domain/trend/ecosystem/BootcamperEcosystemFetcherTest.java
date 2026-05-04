package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootcamperEcosystemFetcherTest {

    @Test
    @DisplayName("courseList thumbnail 파일명은 CloudFront 과정 썸네일 URL로 매핑된다")
    void bootcamperListThumbnailUrl_mapsFilenameToCdn() {
        assertThat(BootcamperEcosystemFetcher.bootcamperListThumbnailUrl("1777_foo-bar.webp"))
                .isEqualTo(
                        "https://d3op5pvc1439n3.cloudfront.net/course/thumbnail/1777_foo-bar.webp");
    }

    @Test
    @DisplayName("부트캠퍼 /uploads 경로에서 파일명만 추출해 CDN 으로 만든다")
    void bootcamperListThumbnailUrl_extractsNameFromBootcamperUploadsUrl() {
        assertThat(
                        BootcamperEcosystemFetcher.bootcamperListThumbnailUrl(
                                "https://bootcamper.co.kr/uploads/abc.png"))
                .isEqualTo("https://d3op5pvc1439n3.cloudfront.net/course/thumbnail/abc.png");
    }

    @Test
    @DisplayName("이미 과정 썸네일 CDN URL 이면 정규화하여 그대로 반환한다")
    void bootcamperListThumbnailUrl_preservesCloudFrontCourseThumbnail() {
        String u =
                "https://d3op5pvc1439n3.cloudfront.net/course/thumbnail/x.png?ignored=1";
        assertThat(BootcamperEcosystemFetcher.bootcamperListThumbnailUrl(u))
                .isEqualTo(
                        "https://d3op5pvc1439n3.cloudfront.net/course/thumbnail/x.png");
    }

    @Test
    @DisplayName("uploads/ 접두(파일명만 상대경로)도 basename 으로 처리한다")
    void bootcamperListThumbnailUrl_uploadsPrefixRelative() {
        assertThat(BootcamperEcosystemFetcher.bootcamperListThumbnailUrl("uploads/a.webp"))
                .isEqualTo(
                        "https://d3op5pvc1439n3.cloudfront.net/course/thumbnail/a.webp");
    }

    @Test
    @DisplayName("다른 호스트 URL 은 과정 썸네일로 쓰지 않는다")
    void bootcamperListThumbnailUrl_foreignHost_returnsNull() {
        assertThat(BootcamperEcosystemFetcher.bootcamperListThumbnailUrl(
                        "https://evil.example.com/phish.png"))
                .isNull();
    }

    @Test
    @DisplayName("경로 순회 문자열은 거부한다")
    void bootcamperListThumbnailUrl_rejectsDotDot() {
        assertThat(BootcamperEcosystemFetcher.bootcamperListThumbnailUrl("../x.png"))
                .isNull();
    }

    @Test
    @DisplayName("허용 분류 집합에 웹·앱·클라우드·기획·AI·데이터 가 포함된다")
    void includedBootcampClassifies_containsExpectedUiCategories() {
        assertThat(BootcamperEcosystemFetcher.INCLUDED_BOOTCAMP_CLASSIFIES)
                .containsExactlyInAnyOrder("웹개발", "앱개발", "클라우드/보안", "PM/기획", "AI/ML", "데이터");
    }
}
