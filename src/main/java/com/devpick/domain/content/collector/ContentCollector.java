package com.devpick.domain.content.collector;

/**
 * 플랫폼별 콘텐츠 수집기 공통 인터페이스.
 *
 * <p>새로운 수집 플랫폼 추가 시 이 인터페이스를 구현하고
 * {@code content_sources} 테이블에 대응 소스를 등록한다.
 *
 * <p>확장 예시:
 * <ul>
 *   <li>StackOverflowCollector — API 수집 (CC BY-SA 4.0)</li>
 *   <li>VelogCollector        — GraphQL 수집</li>
 * </ul>
 */
public interface ContentCollector {

    /**
     * 콘텐츠를 수집하고 저장한다.
     *
     * @param query 수집 시 사용할 질의어 (태그, 키워드 등 수집기별 의미가 다를 수 있음)
     * @return 신규 저장된 콘텐츠 수
     */
    int collect(String query);

    /**
     * 이 수집기가 담당하는 소스 이름 (content_sources.name 과 일치해야 함).
     *
     * @return 소스 이름 (예: "Stack Overflow", "Velog")
     */
    String sourceName();
}
