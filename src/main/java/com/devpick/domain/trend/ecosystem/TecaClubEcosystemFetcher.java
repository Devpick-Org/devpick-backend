package com.devpick.domain.trend.ecosystem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테카 IT 동아리 페이지에서 배포하는 {@code script.js}의 {@code Club} 객체를 파싱합니다.
 * (테카 사이트 스크립트 변경 시 깨질 수 있어 로그로 모니터링 필요)
 */
@Slf4j
@Component
public class TecaClubEcosystemFetcher {

    private static final String SCRIPT_URL = "https://page.teca-official.co.kr/script.js";
    private static final String HOME = "https://page.teca-official.co.kr/";
    private static final int MAX_ITEMS = 80;
    private static final String UA = "TraceApp/1.0 (ecosystem trends; +https://traceapp-orcin.vercel.app)";

    /** {@code hasDevPosition} 과 동일하게 Field 키 기준 제외 목록 (NON_DEV_FIELDS 의 name 대응). */
    private static final Set<String> NON_DEV_FIELD_KEYS = Set.of(
            "MARKETING", "MANAGEMENT", "PM", "DESIGN", "UX");

    private static final Pattern FIELD_REFS = Pattern.compile("Field\\.(\\w+)");

    private final WebClient webClient;

    public TecaClubEcosystemFetcher(WebClient webClient) {
        this.webClient = webClient;
    }

    List<EcosystemTrendItem> fetch() {
        try {
            String script = webClient.get()
                    .uri(SCRIPT_URL)
                    .header("Accept", "*/*")
                    .header("User-Agent", UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();
            if (script == null || script.isBlank()) {
                return List.of();
            }
            return parseClubBlock(script);
        } catch (WebClientResponseException ex) {
            log.warn("테카 script.js 조회 실패: {}", ex.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.warn("테카 수집 예외: {}", e.getMessage());
            return List.of();
        }
    }

    List<EcosystemTrendItem> parseClubBlock(String script) {
        String startMark = "const Club = {";
        String endMark = "const BootcampCost = {";
        int s = script.indexOf(startMark);
        int e = script.indexOf(endMark, s + startMark.length());
        if (s < 0 || e < 0 || e <= s) {
            log.warn("테카 Club/BootcampCost 경계를 찾지 못했습니다.");
            return List.of();
        }
        int bodyStart = s + startMark.length();
        String raw = script.substring(bodyStart, e).trim();
        if (raw.endsWith("};")) {
            raw = raw.substring(0, raw.length() - 2).trim();
        } else if (raw.endsWith("}")) {
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        raw = stripLineComments(raw);
        List<String> chunks = splitTopLevelEntries(raw);
        List<EcosystemTrendItem> items = new ArrayList<>();
        for (String chunk : chunks) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            Matcher keyM = Pattern.compile("^\\s*(\\w+)\\s*:\\s*\\{").matcher(chunk);
            if (!keyM.find()) {
                continue;
            }
            String key = keyM.group(1);
            int open = chunk.indexOf('{', keyM.start());
            int close = findMatchingBrace(chunk, open);
            if (close < 0) {
                continue;
            }
            String obj = chunk.substring(open + 1, close);
            String name = findJsStringField(obj, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String link = findJsStringField(obj, "link");
            String recruitStart = findJsStringField(obj, "recruitStart");
            String recruitEnd = findJsStringField(obj, "recruitEnd");
            if (!hasDevRole(obj)) {
                continue;
            }
            String id = "teca:" + key;
            String detail = (link == null || link.isBlank()) ? HOME : link;
            String lineRecruit = ((recruitStart == null ? "" : recruitStart) + " ~ " + (recruitEnd == null ? "" : recruitEnd))
                    .trim();

            List<String> tags = new ArrayList<>();
            tags.add("IT 연합 동아리");
            Matcher fm = FIELD_REFS.matcher(obj);
            while (fm.find()) {
                tags.add(fm.group(1));
            }

            items.add(new EcosystemTrendItem(
                    id,
                    EcosystemTrendCategory.CLUB,
                    name,
                    "IT 연합 동아리 모음",
                    null,
                    detail,
                    lineRecruit.isEmpty() ? null : "서류 접수 " + lineRecruit,
                    null,
                    null,
                    tags.stream().distinct().toList(),
                    "page.teca-official.co.kr"));
        }
        return items;
    }

    private static boolean hasDevRole(String objBody) {
        Matcher m = FIELD_REFS.matcher(objBody);
        while (m.find()) {
            if (!NON_DEV_FIELD_KEYS.contains(m.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String findJsStringField(String objBody, String field) {
        Pattern p = Pattern.compile(
                "(?:^|[,\\n])\\s*" + Pattern.quote(field) + "\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
                Pattern.MULTILINE);
        Matcher m = p.matcher(objBody);
        if (!m.find()) {
            return null;
        }
        return unescapeJs(m.group(1));
    }

    private static String unescapeJs(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String stripLineComments(String body) {
        return body.replaceAll("(?m)^\\s*//.*$", "");
    }

    /** 최상위 {@code KEY: { ... }} 항목으로 분리 (중괄호 깊이 기준). */
    private static List<String> splitTopLevelEntries(String inner) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        int n = inner.length();
        while (i < n) {
            while (i < n && (Character.isWhitespace(inner.charAt(i)) || inner.charAt(i) == ',')) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int colon = inner.indexOf(':', i);
            if (colon < 0) {
                break;
            }
            int brace = inner.indexOf('{', colon);
            if (brace < 0) {
                break;
            }
            int end = findMatchingBrace(inner, brace);
            if (end < 0) {
                break;
            }
            parts.add(inner.substring(i, end + 1));
            i = end + 1;
        }
        return parts;
    }

    static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
