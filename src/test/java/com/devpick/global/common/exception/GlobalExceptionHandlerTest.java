package com.devpick.global.common.exception;

import com.devpick.domain.user.controller.AuthController;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot 4.x: @WebMvcTest 제거됨, standaloneSetup 사용
// Jackson 3.x: tools.jackson.databind.ObjectMapper 사용
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthController authController;

    /** handleBindException / handleMethodArgumentTypeMismatch / handleNoResourceFound 테스트용 컨트롤러 */
    @RestController
    static class TypeTestController {
        record SearchForm(@NotBlank String keyword) {}

        @GetMapping("/test/search")
        public String search(@Valid @ModelAttribute SearchForm form) {
            return form.keyword();
        }

        @GetMapping("/test/item/{id}")
        public String item(@PathVariable Integer id) {
            return id.toString();
        }

        @GetMapping("/test/resource-not-found")
        public String resourceNotFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/resource-not-found", "resource-not-found");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController, new TypeTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("DevpickException 발생 시 에러 코드와 메시지가 반환된다")
    void handleDevpickException() throws Exception {
        // given
        given(authService.signup(any())).willThrow(new DevpickException(ErrorCode.AUTH_DUPLICATE_EMAIL));

        Map<String, String> request = Map.of(
                "email", "test@devpick.kr",
                "password", "password123!",
                "nickname", "하영"
        );

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("@Valid 검증 실패 시 400과 필드 에러가 반환된다")
    void handleValidationException() throws Exception {
        // given — 이메일 형식 오류
        Map<String, String> request = Map.of(
                "email", "invalid-email",
                "password", "password123!",
                "nickname", "하영"
        );

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 500을 반환한다")
    void handleUnexpectedException_returns500() throws Exception {
        // given
        given(authService.signup(any())).willThrow(new RuntimeException("unexpected"));

        Map<String, String> request = Map.of(
                "email", "test@devpick.kr",
                "password", "password123!",
                "nickname", "하영"
        );

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_005"));
    }

    @Test
    @DisplayName("잘못된 JSON 형식 요청 시 400과 GLOBAL_400_1이 반환된다")
    void handleHttpMessageNotReadable_returns400() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GLOBAL_400_1"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 요청 시 405와 GLOBAL_405가 반환된다")
    void handleMethodNotAllowed_returns405() throws Exception {
        // when & then — /auth/signup은 POST만 지원
        mockMvc.perform(get("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GLOBAL_405"));
    }

    @Test
    @DisplayName("@ModelAttribute 바인딩 오류 시 400과 COMMON_001이 반환된다")
    void handleBindException_returns400() throws Exception {
        // when & then — keyword 파라미터 없이 요청 → @NotBlank 검증 실패 → BindException
        mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("경로 변수 타입 불일치 시 400과 GLOBAL_400_2가 반환된다")
    void handleMethodArgumentTypeMismatch_returns400() throws Exception {
        // when & then — Integer 타입 PathVariable에 문자열 전달 → MethodArgumentTypeMismatchException
        mockMvc.perform(get("/test/item/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GLOBAL_400_2"));
    }

    @Test
    @DisplayName("NoResourceFoundException 발생 시 404와 GLOBAL_404가 반환된다")
    void handleNoResourceFound_returns404() throws Exception {
        // when & then — standaloneSetup은 NoHandlerFoundException을 던지므로 컨트롤러에서 직접 던짐
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GLOBAL_404"));
    }
}
