package com.devpick.domain.user.service;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import org.mockito.Mockito;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.point.entity.Badge;
import com.devpick.domain.point.entity.UserBadge;
import com.devpick.domain.point.repository.UserBadgeRepository;
import com.devpick.domain.user.dto.PublicUserProfileResponse;
import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
import com.devpick.domain.user.dto.UserProfileUpdateResponse;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private UserTagRepository userTagRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private com.devpick.domain.point.service.BadgeService badgeService;
    @Mock
    private UserBadgeRepository userBadgeRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private FileStorageService fileStorageService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("test@devpick.kr")
                .nickname("테스트유저")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
    }

    @Test
    @DisplayName("getPublicProfile — 활성 사용자 공개 프로필 반환")
    void getPublicProfile_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of());
        given(postRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of());
        given(answerRepository.findByUserIdWithPost(userId)).willReturn(List.of());

        PublicUserProfileResponse response = userService.getPublicProfile(userId);

        assertThat(response.nickname()).isEqualTo("테스트유저");
        assertThat(response.job()).isEqualTo(Job.BACKEND);
        assertThat(response.badges()).isEmpty();
        assertThat(response.recentPosts()).isEmpty();
        assertThat(response.recentAnswers()).isEmpty();
    }

    @Test
    @DisplayName("getPublicProfile — 배지/게시글/답변 포함 시 매핑 정상 반환")
    void getPublicProfile_withData_returnsMappedFields() {
        Badge badge = Badge.builder().id("first_question").name("첫 질문왕").description("").sortOrder(1).build();
        UserBadge userBadge = UserBadge.builder().user(user).badge(badge).build();

        Post post = Post.builder().user(user).title("Spring 질문").content("내용").level(Level.JUNIOR).build();

        Answer answer = Answer.builder().post(post).user(user).content("답변 내용").build();

        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of(userBadge));
        given(postRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of(post));
        given(answerRepository.findByUserIdWithPost(userId)).willReturn(List.of(answer));

        PublicUserProfileResponse response = userService.getPublicProfile(userId);

        assertThat(response.badges()).hasSize(1);
        assertThat(response.badges().get(0).badgeId()).isEqualTo("first_question");
        assertThat(response.recentPosts()).hasSize(1);
        assertThat(response.recentPosts().get(0).title()).isEqualTo("Spring 질문");
        assertThat(response.recentAnswers()).hasSize(1);
        assertThat(response.recentAnswers().get(0).postTitle()).isEqualTo("Spring 질문");
    }

    @Test
    @DisplayName("getPublicProfile — createdAt 있을 때 Instant 변환 정상")
    void getPublicProfile_withCreatedAt_convertsToInstant() {
        Post mockPost = Mockito.mock(Post.class);
        Mockito.when(mockPost.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(mockPost.getTitle()).thenReturn("질문");
        Mockito.when(mockPost.getCreatedAt()).thenReturn(LocalDateTime.now());

        Answer mockAnswer = Mockito.mock(Answer.class);
        Mockito.when(mockAnswer.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(mockAnswer.getPost()).thenReturn(mockPost);
        Mockito.when(mockAnswer.getCreatedAt()).thenReturn(LocalDateTime.now());

        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of());
        given(postRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of(mockPost));
        given(answerRepository.findByUserIdWithPost(userId)).willReturn(List.of(mockAnswer));

        PublicUserProfileResponse response = userService.getPublicProfile(userId);

        assertThat(response.recentPosts().get(0).createdAt()).isNotNull();
        assertThat(response.recentAnswers().get(0).createdAt()).isNotNull();
    }

    @Test
    @DisplayName("getPublicProfile — 동일 게시글에 답변 여러 개일 때 recentAnswers에 게시글 1개만 포함")
    void getPublicProfile_multipleAnswersOnSamePost_deduplicatesRecentAnswers() {
        Post post = Post.builder().user(user).title("Spring 질문").content("내용").level(Level.JUNIOR).build();

        Answer answer1 = Mockito.mock(Answer.class);
        Answer answer2 = Mockito.mock(Answer.class);
        UUID postId = UUID.randomUUID();
        UUID answer1Id = UUID.randomUUID();
        UUID answer2Id = UUID.randomUUID();

        Post mockPost = Mockito.mock(Post.class);
        Mockito.when(mockPost.getId()).thenReturn(postId);
        Mockito.when(mockPost.getTitle()).thenReturn("Spring 질문");

        Mockito.when(answer1.getId()).thenReturn(answer1Id);
        Mockito.when(answer1.getPost()).thenReturn(mockPost);
        Mockito.when(answer1.getCreatedAt()).thenReturn(null);

        Mockito.when(answer2.getId()).thenReturn(answer2Id);
        Mockito.when(answer2.getPost()).thenReturn(mockPost);
        Mockito.when(answer2.getCreatedAt()).thenReturn(null);

        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of());
        given(postRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of(post));
        given(answerRepository.findByUserIdWithPost(userId)).willReturn(List.of(answer1, answer2));

        PublicUserProfileResponse response = userService.getPublicProfile(userId);

        assertThat(response.recentAnswers()).hasSize(1);
        assertThat(response.recentAnswers().get(0).postId()).isEqualTo(postId);
        assertThat(response.recentAnswers().get(0).answerId()).isEqualTo(answer1Id); // 첫 번째(최신) 답변 유지
    }

    @Test
    @DisplayName("getPublicProfile — 비활성 사용자 USER_NOT_FOUND 예외")
    void getPublicProfile_inactiveUser_throwsUserNotFound() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("getProfile — 활성 사용자 프로필 반환")
    void getProfile_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(userId);

        assertThat(response.email()).isEqualTo("test@devpick.kr");
        assertThat(response.nickname()).isEqualTo("테스트유저");
    }

    @Test
    @DisplayName("getProfile — 비활성 사용자 USER_NOT_FOUND 예외")
    void getProfile_inactiveUser_throwsUserNotFound() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("updateProfile — 닉네임 변경 성공")
    void updateProfile_nickname_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("새닉네임", userId)).willReturn(false);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("새닉네임", null, null, null, null);

        UserProfileUpdateResponse response = userService.updateProfile(userId, request);

        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("updateProfile — 닉네임 중복 시 USER_DUPLICATE_NICKNAME 예외")
    void updateProfile_duplicateNickname_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("중복닉", userId)).willReturn(true);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("중복닉", null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_DUPLICATE_NICKNAME));
    }

    @Test
    @DisplayName("updateProfile — 태그 변경 시 응답에 새 태그가 포함된다")
    void updateProfile_tags_returnsUpdatedTags() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        Tag reactTag = Tag.builder().name("React").build();
        given(tagRepository.findByNameIgnoreCase("React")).willReturn(Optional.of(reactTag));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("React"));

        UserProfileUpdateResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactly("React");
    }

    @Test
    @DisplayName("updateProfile — 태그 재설정 시 중복 없이 교체된다")
    void updateProfile_tags_replacedWithoutDuplicate() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        Tag reactTag = Tag.builder().name("React").build();
        Tag tsTag = Tag.builder().name("TypeScript").build();
        given(tagRepository.findByNameIgnoreCase("React")).willReturn(Optional.of(reactTag));
        given(tagRepository.findByNameIgnoreCase("TypeScript")).willReturn(Optional.of(tsTag));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("React", "TypeScript"));

        UserProfileUpdateResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactlyInAnyOrder("React", "TypeScript");
        assertThat(response.tags()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("updateProfile — 대소문자 다른 태그 입력 시 기존 tag row 재사용, 신규 row 미생성")
    void updateProfile_tags_caseInsensitive_reusesExistingTag() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        Tag cssTag = Tag.builder().name("CSS").build();
        given(tagRepository.findByNameIgnoreCase("css")).willReturn(Optional.of(cssTag));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("css"));

        UserProfileUpdateResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactly("CSS");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile — 존재하지 않는 태그는 신규 생성된다")
    void updateProfile_tags_unknown_createsNewRow() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        Tag newTag = Tag.builder().name("Zig").build();
        given(tagRepository.findByNameIgnoreCase("Zig")).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willReturn(newTag);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("Zig"));

        UserProfileUpdateResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactly("Zig");
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    @DisplayName("deleteAccount — 소프트 삭제 및 리프레시 토큰 무효화")
    void deleteAccount_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        userService.deleteAccount(userId);

        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("deleteAccount — 비활성 사용자 USER_NOT_FOUND 예외")
    void deleteAccount_inactiveUser_throwsUserNotFound() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    @DisplayName("resolvePreferredAiLevel — 명시 값은 trim 후 그대로")
    void resolvePreferredAiLevel_explicit_returnsTrimmed() {
        assertThat(userService.resolvePreferredAiLevel(userId, "  MIDDLE  ")).isEqualTo("MIDDLE");
    }

    @Test
    @DisplayName("resolvePreferredAiLevel — 비어 있고 로그인 시 프로필 level")
    void resolvePreferredAiLevel_blank_usesProfileLevel() {
        User middle = User.builder()
                .email("m@x.kr")
                .nickname("m")
                .job(Job.BACKEND)
                .level(Level.MIDDLE)
                .build();
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(middle));

        assertThat(userService.resolvePreferredAiLevel(userId, null)).isEqualTo("MIDDLE");
        assertThat(userService.resolvePreferredAiLevel(userId, "   ")).isEqualTo("MIDDLE");
    }

    @Test
    @DisplayName("resolvePreferredAiLevel — 비로그인이면 JUNIOR")
    void resolvePreferredAiLevel_anonymous_defaultsJunior() {
        assertThat(userService.resolvePreferredAiLevel(null, null)).isEqualTo("JUNIOR");
    }

    @Test
    @DisplayName("resolvePreferredAiLevel — 사용자 없으면 JUNIOR")
    void resolvePreferredAiLevel_userMissing_defaultsJunior() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThat(userService.resolvePreferredAiLevel(userId, null)).isEqualTo("JUNIOR");
    }
}
