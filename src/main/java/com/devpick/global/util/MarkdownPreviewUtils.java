package com.devpick.global.util;

import java.util.regex.Pattern;

/**
 * 목록·피드용 미리보기에서 마크다운 문법을 제거해 평문에 가깝게 만든다.
 */
public final class MarkdownPreviewUtils {

    private static final Pattern FENCED_CODE = Pattern.compile("(?s)```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern HEADER = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern STRONG_STAR = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern STRONG_UNDER = Pattern.compile("__([^_]+)__");
    private static final Pattern EMPH_STAR = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern EMPH_UNDER = Pattern.compile("(?<!_)_([^_\\n]+)_(?!_)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MarkdownPreviewUtils() {
    }

    public static String stripForPreview(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String s = FENCED_CODE.matcher(text).replaceAll(" ");
        s = INLINE_CODE.matcher(s).replaceAll("$1");
        s = IMAGE.matcher(s).replaceAll(" ");
        s = LINK.matcher(s).replaceAll("$1");
        s = HEADER.matcher(s).replaceAll("");
        s = STRONG_STAR.matcher(s).replaceAll("$1");
        s = STRONG_UNDER.matcher(s).replaceAll("$1");
        s = EMPH_STAR.matcher(s).replaceAll("$1");
        s = EMPH_UNDER.matcher(s).replaceAll("$1");
        s = HTML_TAG.matcher(s).replaceAll(" ");
        s = WHITESPACE.matcher(s.trim()).replaceAll(" ");
        return s;
    }
}
