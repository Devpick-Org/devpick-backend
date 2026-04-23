package com.devpick.domain.content.service;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentViewLog;
import com.devpick.domain.content.repository.ContentViewLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentViewLogServiceTest {

    @InjectMocks
    private ContentViewLogService contentViewLogService;

    @Mock
    private ContentViewLogRepository contentViewLogRepository;

    private final Content content = Content.builder()
            .title("테스트 콘텐츠")
            .author("작성자")
            .canonicalUrl("https://example.com/test")
            .build();

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("정상 UA — 뷰 로그를 저장한다")
    void record_normalUA_savesLog() {
        contentViewLogService.record(content, userId, "Mozilla/5.0");

        verify(contentViewLogRepository).save(any(ContentViewLog.class));
    }

    @Test
    @DisplayName("null UA — 봇 아님으로 판단해 저장한다")
    void record_nullUA_savesLog() {
        contentViewLogService.record(content, userId, null);

        verify(contentViewLogRepository).save(any(ContentViewLog.class));
    }

    @Test
    @DisplayName("빈 문자열 UA — 저장한다")
    void record_emptyUA_savesLog() {
        contentViewLogService.record(content, userId, "");

        verify(contentViewLogRepository).save(any(ContentViewLog.class));
    }

    @Test
    @DisplayName("bot UA — 저장하지 않는다")
    void record_botUA_skipsSave() {
        contentViewLogService.record(content, userId, "Googlebot/2.1");

        verify(contentViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("crawler UA — 저장하지 않는다")
    void record_crawlerUA_skipsSave() {
        contentViewLogService.record(content, userId, "Apachenutch/crawler");

        verify(contentViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("spider UA — 저장하지 않는다")
    void record_spiderUA_skipsSave() {
        contentViewLogService.record(content, userId, "spider-agent/1.0");

        verify(contentViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("scraper UA — 저장하지 않는다")
    void record_scraperUA_skipsSave() {
        contentViewLogService.record(content, userId, "scraperapi/1.0");

        verify(contentViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("대문자 BOT UA — 대소문자 무관하게 저장하지 않는다")
    void record_caseInsensitiveBot_skipsSave() {
        contentViewLogService.record(content, userId, "BOT/1.0");

        verify(contentViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("userId가 null이어도 저장한다 (비로그인 허용)")
    void record_nullUserId_savesLog() {
        contentViewLogService.record(content, null, "Mozilla/5.0");

        verify(contentViewLogRepository).save(any(ContentViewLog.class));
    }
}
