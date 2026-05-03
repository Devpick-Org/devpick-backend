package com.devpick.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** 인프런·부트캠퍼 등 OG/Next HTML 이 256KB 기본 한도를 넘어 조용히 실패하는 것을 피합니다. */
    private static final int MAX_IN_MEMORY_BYTES = 3 * 1024 * 1024;

    @Bean
    public WebClient webClient() {
        ExchangeStrategies strategies =
                ExchangeStrategies.builder().codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES)).build();
        return WebClient.builder().exchangeStrategies(strategies).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
