package com.devpick.domain.community.client;

import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiAnswerClientTest {

    @InjectMocks
    private AiAnswerClient aiAnswerClient;

    @Mock
    private WebClient webClient;

    private Post post;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiAnswerClient, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(aiAnswerClient, "internalKey", "test-key");

        user = User.builder().email("test@devpick.kr").nickname("tester").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        post = Post.builder()
                .title("Spring 질문")
                .content("내용입니다")
                .level(Level.JUNIOR)
                .user(user)
                .build();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientChain(Object returnValue) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(uriSpec);
        given(uriSpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.bodyValue(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);

        if (returnValue instanceof Throwable) {
            given(responseSpec.bodyToMono(AiAnswerClient.AiAnswerFastApiResponse.class))
                    .willReturn(Mono.error((Throwable) returnValue));
        } else {
            given(responseSpec.bodyToMono(AiAnswerClient.AiAnswerFastApiResponse.class))
                    .willReturn(Mono.justOrEmpty((AiAnswerClient.AiAnswerFastApiResponse) returnValue));
        }
    }

    @Test
    @DisplayName("AI 서버 정상 응답 시 answerContent를 반환한다")
    void generateAnswer_success_returnsContent() {
        AiAnswerClient.AiAnswerFastApiResponse fakeResponse =
                new AiAnswerClient.AiAnswerFastApiResponse("AI가 생성한 답변");
        mockWebClientChain(fakeResponse);

        String result = aiAnswerClient.generateAnswer(post, null);

        assertThat(result).isEqualTo("AI가 생성한 답변");
    }

    @Test
    @DisplayName("refined 데이터가 있으면 refinedTitle/refinedContent를 사용한다")
    void generateAnswer_withRefinedQuestion_usesRefinedData() {
        AiQuestion aiQuestion = AiQuestion.builder()
                .post(post)
                .originalTitle("Spring 질문")
                .refinedTitle("Spring IoC란 무엇인가요?")
                .refinedContent("Spring의 IoC 컨테이너 동작 방식을 설명해주세요.")
                .build();

        AiAnswerClient.AiAnswerFastApiResponse fakeResponse =
                new AiAnswerClient.AiAnswerFastApiResponse("refined 기반 AI 답변");
        mockWebClientChain(fakeResponse);

        String result = aiAnswerClient.generateAnswer(post, aiQuestion);

        assertThat(result).isEqualTo("refined 기반 AI 답변");
    }

    @Test
    @DisplayName("AI 서버가 null을 반환하면 AI_SERVER_ERROR 예외가 발생한다")
    void generateAnswer_nullResponse_throwsAiServerError() {
        mockWebClientChain(null);

        assertThatThrownBy(() -> aiAnswerClient.generateAnswer(post, null))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("WebClientResponseException 발생 시 AI_SERVER_ERROR 예외가 발생한다")
    void generateAnswer_webClientException_throwsAiServerError() {
        mockWebClientChain(WebClientResponseException.create(500, "Internal Server Error", null, null, null));

        assertThatThrownBy(() -> aiAnswerClient.generateAnswer(post, null))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("answerContent가 null이면 AI_SERVER_ERROR 예외가 발생한다")
    void generateAnswer_nullContent_throwsAiServerError() {
        AiAnswerClient.AiAnswerFastApiResponse fakeResponse =
                new AiAnswerClient.AiAnswerFastApiResponse(null);
        mockWebClientChain(fakeResponse);

        assertThatThrownBy(() -> aiAnswerClient.generateAnswer(post, null))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
