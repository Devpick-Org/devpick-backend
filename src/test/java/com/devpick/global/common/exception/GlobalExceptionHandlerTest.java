package com.devpick.global.common.exception;

import com.devpick.global.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("DevpickException → ErrorCode에 맞는 HTTP 상태코드와 에러 응답 반환")
    void handleDevpickException() {
        // given
        DevpickException exception = new DevpickException(ErrorCode.USER_NOT_FOUND);

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleDevpickException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("USER_001");
    }

    @Test
    @DisplayName("DevpickException — 커스텀 메시지 사용 시 해당 메시지 반환")
    void handleDevpickException_customMessage() {
        // given
        DevpickException exception = new DevpickException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다.");

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleDevpickException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().message()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("예상치 못한 예외 → 500 Internal Server Error 반환")
    void handleException() {
        // given
        Exception exception = new RuntimeException("unexpected error");

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_005");
    }
}
