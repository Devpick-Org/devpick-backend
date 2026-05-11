package com.devpick.domain.content.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 콘텐츠 검색어를 동의어(한↔영·표기 차이 등)까지 포함해 확장한다. DB가 LIKE 기반이라
 * 사용자가 적은 문구와 다른 표기가 제목·번역 제목 등에 있어도 노출되게 한다.
 */
public final class ContentSearchQueryExpander {

    private static final String[][] SYNONYM_GROUPS =
            new String[][] {
                    {"netflix", "넷플릭스"},
                    {"google", "구글"},
                    {"microsoft", "마이크로소프트"},
                    {"amazon", "아마존"},
                    {"kubernetes", "쿠버네티스", "k8s"},
                    {"docker", "도커"},
                    {"redis", "레디스"},
                    {"kafka", "카프카"},
                    {"elasticsearch", "엘라스틱서치"},
                    {"mongodb", "몽고db", "몽고디비"},
                    {"nginx", "엔진엑스"},
                    {"graphql", "그래프ql"},
                    {"github", "깃허브", "깃헙"},
                    {"gitlab", "깃랩"},
                    {"websocket", "웹소켓"},
                    {"pytorch", "파이토치"},
                    {"tensorflow", "텐서플로"},
                    {"openai", "오픈ai"},
                    {"facebook", "페이스북"},
                    {"apple", "애플"},
                    {"nvidia", "엔비디아"},
                    {"android", "안드로이드"},
                    {"kotlin", "코틀린"},
                    {"typescript", "타입스크립트"},
                    {"javascript", "자바스크립트"},
                    {"postgresql", "포스트그레"},
                    {"mysql", "마이에스큐엘"},
                    {"slack", "슬랙"},
                    {"youtube", "유튜브"},
                    {"linkedin", "링크드인"},
                    {"terraform", "테라폼"},
            };

    private ContentSearchQueryExpander() {}

    /** @return 패턴 검색용으로 소문자(라틴만) 정규화된 구문 목록 (순서 보존, 중복 없음). */
    public static List<String> expand(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        String normalized = rawQuery.strip().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(normalized);
        addTriggeredSynonyms(normalized, out);
        List<String> list = new ArrayList<>(out);
        list.removeIf(String::isBlank);
        return list;
    }

    private static void addTriggeredSynonyms(String normalizedLowerQuery, Set<String> out) {
        for (String[] group : SYNONYM_GROUPS) {
            boolean hit = false;
            for (String alias : group) {
                String a = alias.toLowerCase(Locale.ROOT);
                if (normalizedLowerQuery.contains(a)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                for (String alias : group) {
                    out.add(alias.toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
