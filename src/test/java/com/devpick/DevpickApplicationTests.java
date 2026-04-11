package com.devpick;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 Spring 컨텍스트 + PostgreSQL/Redis 등이 필요한 스모크 테스트.
 * 로컬에서 DB 미기동 시 실패하므로 CI({@code CI=true})에서만 실행한다.
 */
@SpringBootTest(properties = {"jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1kZXZwaWNrLXRlc3Rpbmc="})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class DevpickApplicationTests {

    @Test
    void contextLoads() {
    }
}
