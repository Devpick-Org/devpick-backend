package com.devpick.domain.content.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentCollectorSchedulerTest {

    @InjectMocks
    private ContentCollectorScheduler scheduler;

    @Mock
    private ContentCollector stackOverflowCollector;

    @Mock
    private ContentCollector velogCollector;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "collectors",
                List.of(stackOverflowCollector, velogCollector));
        ReflectionTestUtils.setField(scheduler, "stackOverflowTags",
                "java;spring-boot;kotlin;python");
    }

    @Test
    @DisplayName("collect — SO는 stackOverflowTags로, Velog는 null로 collect 호출")
    void collect_callsEachCollectorWithCorrectQuery() {
        given(stackOverflowCollector.sourceName()).willReturn("Stack Overflow");
        given(velogCollector.sourceName()).willReturn("Velog");
        given(stackOverflowCollector.collect("java;spring-boot;kotlin;python")).willReturn(10);
        given(velogCollector.collect(null)).willReturn(20);

        scheduler.collect();

        verify(stackOverflowCollector).collect("java;spring-boot;kotlin;python");
        verify(velogCollector).collect(null);
    }

    @Test
    @DisplayName("collect — 수집기 하나가 예외 발생해도 나머지 수집기 계속 실행")
    void collect_oneCollectorFails_continuesOthers() {
        given(stackOverflowCollector.sourceName()).willReturn("Stack Overflow");
        given(velogCollector.sourceName()).willReturn("Velog");
        given(stackOverflowCollector.collect(any())).willThrow(new RuntimeException("API error"));
        given(velogCollector.collect(null)).willReturn(20);

        scheduler.collect();

        verify(velogCollector).collect(null);
    }

    @Test
    @DisplayName("collect — 수집기가 없으면 아무것도 실행하지 않음")
    void collect_noCollectors_doesNothing() {
        ReflectionTestUtils.setField(scheduler, "collectors", List.of());

        scheduler.collect();

        verifyNoInteractions(stackOverflowCollector, velogCollector);
    }
}