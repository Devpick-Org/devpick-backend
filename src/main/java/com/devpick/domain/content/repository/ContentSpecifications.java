package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.user.entity.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class ContentSpecifications {

    private ContentSpecifications() {}

    /** 수집 RSS 피드: 소스 이름이 YouTube 인 행은 목록에서 제외한다 (대소문자 무시). */
    public static Specification<Content> notYoutubeFeed() {
        return (root, query, cb) -> {
            var sourceJoin = root.join("source");
            return cb.notEqual(cb.lower(sourceJoin.get("name")), "youtube");
        };
    }

    public static Specification<Content> available() {
        return (root, query, cb) -> cb.isTrue(root.get("isAvailable"));
    }

    /**
     * 검색어마다 {@code title} / {@code translatedTitle} / {@code author}에 대해 부분 일치 —
     * 여러 용어는 OR (한 용어라도 맞으면 매칭). 용어가 비면 항상 true.
     */
    public static Specification<Content> keywordMatchesAny(Collection<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Locale lc = Locale.ROOT;
            List<Predicate> anyTermPredicates = new ArrayList<>();
            for (String raw : terms) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String pattern = "%" + raw.trim().toLowerCase(lc) + "%";

                Predicate titlePred =
                        cb.like(cb.lower(cb.coalesce(root.get("title"), "")), pattern);
                Predicate transPred =
                        cb.like(cb.lower(cb.coalesce(root.get("translatedTitle"), "")), pattern);
                Predicate authorPred =
                        cb.like(cb.lower(cb.coalesce(root.get("author"), "")), pattern);
                anyTermPredicates.add(cb.or(titlePred, transPred, authorPred));
            }
            if (anyTermPredicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.or(anyTermPredicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 태그명(소문자로 정규화된 이름) 포함 — 기존 쿼리와 같이 하나라도 포함되면 됨(IN).
     * DISTINCT로 중복 행 방지.
     */
    public static Specification<Content> taggedWithAny(Collection<String> lowerTagNames) {
        if (lowerTagNames == null || lowerTagNames.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Content, ContentTag> ct = root.join("contentTags", JoinType.LEFT);
            Join<ContentTag, Tag> tag = ct.join("tag", JoinType.LEFT);
            return cb.lower(tag.get("name")).in(lowerTagNames);
        };
    }
}
