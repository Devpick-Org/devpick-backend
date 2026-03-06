package com.devpick.global.common.exception;

import com.devpick.global.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── DevpickException ──────────────────────────────────────────────────────

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
        assertThat(response.getBody().error().message()).isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
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
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_001");
        assertThat(response.getBody().error().message()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("DevpickException — AUTH 에러코드 HTTP 상태 확인")
    void handleDevpickException_authError() {
        // given
        DevpickException exception = new DevpickException(ErrorCode.AUTH_INVALID_TOKEN);

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleDevpickException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_001");
    }

    // ── MethodArgumentNotValidException ───────────────────────────────────────

    @Test
    @DisplayName("MethodArgumentNotValidException — 필드 오류 메시지 반환")
    void handleValidationException_withFieldError() {
        // given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "이메일 형식이 올바르지 않습니다."));
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_001");
        assertThat(response.getBody().error().message()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 여러 필드 오류 시 첫 번째 메시지만 반환")
    void handleValidationException_withMultipleFieldErrors() {
        // given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "이메일이 필요합니다."));
        bindingResult.addError(new FieldError("target", "password", "비밀번호가 필요합니다."));
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().message()).isEqualTo("이메일이 필요합니다.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 필드 오류 없을 시 기본 메시지 반환")
    void handleValidationException_withNoFieldErrors() {
        // given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_001");
        assertThat(response.getBody().error().message()).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    }

    // ── 일반 Exception ────────────────────────────────────────────────────────

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
        assertThat(response.getBody().error().message()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    @DisplayName("NullPointerException → 500 처리")
    void handleException_nullPointer() {
        // given
        Exception exception = new NullPointerException();

        // when
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().success()).isFalse();
    }
}
