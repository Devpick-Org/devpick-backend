package com.devpick.domain.content.service;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentViewLog;
import com.devpick.domain.content.repository.ContentViewLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentViewLogService {

    private static final Pattern BOT_PATTERN = Pattern.compile("(?i)bot|crawler|spider|scraper");

    private final ContentViewLogRepository contentViewLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Content content, UUID userId, String userAgent) {
        if (isBot(userAgent)) {
            log.debug("봇 UA 감지, skip: ua={}", userAgent);
            return;
        }
        contentViewLogRepository.save(
                ContentViewLog.builder()
                        .content(content)
                        .userId(userId)
                        .build()
        );
    }

    private boolean isBot(String userAgent) {
        return userAgent != null && BOT_PATTERN.matcher(userAgent).find();
    }
}
