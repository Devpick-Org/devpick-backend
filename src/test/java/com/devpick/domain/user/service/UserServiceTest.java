package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        UserProfileResponse response = userService.updateProfile(userId, request);

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
        given(tagRepository.findByName("React")).willReturn(Optional.of(reactTag));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("React"));

        UserProfileResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactly("React");
    }

    @Test
    @DisplayName("updateProfile — 태그 재설정 시 중복 없이 교체된다")
    void updateProfile_tags_replacedWithoutDuplicate() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        Tag reactTag = Tag.builder().name("React").build();
        Tag tsTag = Tag.builder().name("TypeScript").build();
        given(tagRepository.findByName("React")).willReturn(Optional.of(reactTag));
        given(tagRepository.findByName("TypeScript")).willReturn(Optional.of(tsTag));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, null, null, null, List.of("React", "TypeScript"));

        UserProfileResponse response = userService.updateProfile(userId, request);

        assertThat(response.tags()).containsExactlyInAnyOrder("React", "TypeScript");
        assertThat(response.tags()).doesNotHaveDuplicates();
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
}
