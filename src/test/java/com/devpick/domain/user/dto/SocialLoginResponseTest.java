package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginResponseTest {

    @Test
    @DisplayName("of() - 신규 유저: 모든 필드가 올바르게 매핑되고 isNewUser=true이다")
    void of_newUser_mapsAllFields() throws Exception {
        User user = createUser("test@devpick.kr", "하영");
        UUID userId = user.getId();

        SocialLoginResponse response = SocialLoginResponse.of("access-token", "refresh-token", user, true);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@devpick.kr");
        assertThat(response.nickname()).isEqualTo("하영");
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.refreshTokenValue()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("of() - 기존 유저: isNewUser=false이다")
    void of_existingUser_isNewUserFalse() throws Exception {
        User user = createUser("existing@devpick.kr", "홍근");

        SocialLoginResponse response = SocialLoginResponse.of("access-token", "refresh-token", user, false);

        assertThat(response.isNewUser()).isFalse();
    }

    private User createUser(String email, String nickname) throws Exception {
        User user = User.createEmailUser(email, "encodedPw", nickname);
        Field idField = user.getClass().getSuperclass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());
        return user;
    }
}