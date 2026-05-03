package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Next.js HTML에서 {@code __NEXT_DATA__} JSON의 {@code pageProps} 노드를 추출합니다.
 */
@Slf4j
public final class NextDataPagePropsExtractor {

    private static final Pattern NEXT_DATA_SCRIPT = Pattern.compile(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
            Pattern.DOTALL);

    private NextDataPagePropsExtractor() {}

    static Optional<JsonNode> extract(ObjectMapper mapper, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        var m = NEXT_DATA_SCRIPT.matcher(html);
        if (!m.find()) {
            log.warn("Next.js __NEXT_DATA__ 블록을 찾지 못했습니다.");
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(m.group(1));
            JsonNode pageProps = root.path("props").path("pageProps");
            return pageProps.isMissingNode() || pageProps.isNull()
                    ? Optional.empty()
                    : Optional.of(pageProps);
        } catch (IOException e) {
            log.warn("Next.js __NEXT_DATA__ 파싱 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
