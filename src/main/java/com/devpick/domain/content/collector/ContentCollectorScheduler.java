package com.devpick.domain.content.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 콘텐츠 수집 스케줄러.
 * 매일 새벽 2시에 모든 수집기를 실행한다.
 *
 * <p>수집기 목록:
 * <ul>
 *   <li>StackOverflowCollector — java;spring-boot;kotlin;python 태그</li>
 *   <li>VelogCollector — 최신 글 수집 (tags 미사용)</li>
 * </ul>
 */
@Slf4j
@Component
public class ContentCollectorScheduler {

    private final List<ContentCollector> collectors;

    @Value("${stackoverflow.collect-tags:java;spring-boot;kotlin;python}")
    private String stackOverflowTags;

    public ContentCollectorScheduler(List<ContentCollector> collectors) {
        this.collectors = collectors;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void collect() {
        log.info("Content collection started.");

        for (ContentCollector collector : collectors) {
            try {
                String query = collector.sourceName().equalsIgnoreCase("Stack Overflow")
                        ? stackOverflowTags
                        : null;
                int saved = collector.collect(query);
                log.info("{} collection done. saved={}", collector.sourceName(), saved);
            } catch (Exception e) {
                log.error("{} collection failed: {}", collector.sourceName(), e.getMessage());
            }
        }

        log.info("Content collection finished.");
    }
}
