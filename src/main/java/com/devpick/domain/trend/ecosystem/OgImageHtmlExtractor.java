package com.devpick.domain.trend.ecosystem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * 페이지 HTML 과 base URI 로 OG / Twitter 이미지 후보 문자열 하나를 선택합니다 (I/O 없음).
 */
final class OgImageHtmlExtractor {

    private OgImageHtmlExtractor() {}

    static String extractPreferredImageHref(String html, String baseUri) {
        if (html == null || html.isBlank() || baseUri == null || baseUri.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parse(html, baseUri, Parser.htmlParser());

        final String[][] metaSelectors = {
                {"meta[property=og:image]", "content"},
                {"meta[property=og:image:url]", "content"},
                {"meta[name=twitter:image]", "content"},
                {"meta[name=twitter:image:src]", "content"},
        };
        for (String[] row : metaSelectors) {
            var el = doc.selectFirst(row[0]);
            if (el == null) {
                continue;
            }
            String cand = nz(el.attr(row[1]));
            String abs = toAbsoluteSafe(baseUri, cand);
            if (abs != null && OgUrlGuards.looksReasonableImageHref(abs)) {
                return abs;
            }
        }
        var link = doc.selectFirst("link[rel=image_src]");
        if (link != null) {
            String abs = nz(link.absUrl("href"));
            if (!abs.isEmpty() && OgUrlGuards.looksReasonableImageHref(abs)) {
                return abs;
            }
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    private static String toAbsoluteSafe(String base, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        candidate = candidate.strip();
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            try {
                return java.net.URI.create(candidate).normalize().toASCIIString();
            } catch (Exception e) {
                return candidate;
            }
        }
        try {
            java.net.URI b = java.net.URI.create(base.strip());
            return b.resolve(candidate).normalize().toASCIIString();
        } catch (Exception e) {
            return null;
        }
    }
}
