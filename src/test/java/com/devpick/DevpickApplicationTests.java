package com.devpick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1kZXZwaWNrLXRlc3Rpbmc="})
@ActiveProfiles("test")
class DevpickApplicationTests {

    @Test
    void contextLoads() {
    }
}
