package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.OAuthUserInfo;
import com.devpick.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 닉네임 생성 로직 (SRP 분리).
 *
 * 생성 정쇝:
 * 1. OAuthUserInfo.name()이 있으면 우선 시도
 * 2. 없거나 중복이면 getNicknamePrefix() 시도 (GitHub: login, Google: email@앞)
 * 3. 아돈 다 중복이면 prefix + "_" + providerId suffix 부착
 */
@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private final UserRepository userRepository;

    /**
     * @param name       제공자가 되돌려준 display name (nullable)
     * @param prefix     provider 니크네임 prefix (ex. GitHub login, Google email 앞부분)
     * @param providerId provider 고유 ID (suffix 시 사용)
     */
    public String generate(String name, String prefix, String providerId) {
        // 1차: display name 시도
        if (name != null && !name.isBlank() && !userRepository.existsByNickname(name)) {
            return name;
        }
        // 2차: prefix 시도
        if (!userRepository.existsByNickname(prefix)) {
            return prefix;
        }
        // 3차: suffix 부착
        return prefix + "_" + providerId;
    }

    /**
     * OAuthUserInfo 기반 오버로드.
     */
    public String generate(String name, OAuthUserInfo userInfo) {
        return generate(name, userInfo.getNicknamePrefix(), userInfo.getProviderId());
    }
}
