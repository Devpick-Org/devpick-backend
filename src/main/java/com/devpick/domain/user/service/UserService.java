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
            userTagRepository.deleteByUserId(userId);
            List<Tag> tags = tagRepository.findByNameIn(request.tags());
            tags.forEach(tag -> userTagRepository.save(
                    UserTag.builder().user(user).tag(tag).build()
            ));
        }

        return UserProfileResponse.from(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findActiveUser(userId);
        user.softDelete();
        refreshTokenRepository.deleteByUser(user);
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
    }
}
