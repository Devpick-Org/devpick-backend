package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final UserTagRepository userTagRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = findActiveUser(userId);

        if (request.nickname() != null &&
                userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new DevpickException(ErrorCode.USER_DUPLICATE_NICKNAME);
        }

        user.updateProfile(request.nickname(), request.profileImage(), request.job(), request.level());

        if (request.tags() != null) {
            List<Tag> tags = findOrCreateTags(request.tags());
            // orphanRemoval flush 순서 문제 방지 — 명시적 DELETE 후 INSERT
            userTagRepository.deleteByUserId(userId);
            userTagRepository.flush();
            user.getUserTags().clear();
            tags.forEach(tag -> user.getUserTags().add(UserTag.builder().user(user).tag(tag).build()));
        }

        return UserProfileResponse.from(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findActiveUser(userId);
        user.softDelete();
        refreshTokenRepository.deleteByUser(user);
    }

    /** 태그명으로 Tag 조회, 없으면 신규 생성 후 반환. */
    private List<Tag> findOrCreateTags(List<String> names) {
        return names.stream()
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
                .toList();
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
    }
}
