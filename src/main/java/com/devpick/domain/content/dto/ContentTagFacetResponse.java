package com.devpick.domain.content.dto;

/** 크롤된 콘텐츠에 붙은 태그 빈도(공개 facet). */
public record ContentTagFacetResponse(
        String name,
        long count,
        String source
) {}
