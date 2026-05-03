package com.devpick.domain.trend.ecosystem;

import java.net.URI;

/** 사용자 제공 URL로 서버측 페치 시 최소 한도의 SSRF/이상 패턴 차단. */
final class OgUrlGuards {

    private OgUrlGuards() {}

    static boolean isAllowedPageFetchUri(String uri) {
        URI u = safeParse(uri);
        if (u == null) {
            return false;
        }
        String scheme = u.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = u.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase().strip();
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            return false;
        }
        if (looksLikePrivateIPv4(host)) {
            return false;
        }
        return true;
    }

    static boolean looksReasonableImageHref(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }
        URI u = safeParse(href.strip());
        if (u == null) {
            return false;
        }
        String scheme = u.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (looksLikePrivateIPv4(u.getHost())) {
            return false;
        }
        String lower = href.toLowerCase();
        return !lower.startsWith("data:") && !lower.contains("javascript:");
    }

    private static boolean looksLikePrivateIPv4(String host) {
        if (host == null || !host.matches("^[\\d.]+$")) {
            return false;
        }
        if (host.startsWith("127.")) {
            return true;
        }
        if (host.startsWith("10.")) {
            return true;
        }
        if (host.startsWith("192.168.")) {
            return true;
        }
        if (!host.startsWith("172.")) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 2 || !parts[0].equals("172")) {
            return false;
        }
        try {
            int n = Integer.parseInt(parts[1]);
            return n >= 16 && n <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static URI safeParse(String raw) {
        try {
            return URI.create(raw.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
