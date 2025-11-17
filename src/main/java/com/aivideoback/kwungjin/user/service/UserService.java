package com.aivideoback.kwungjin.user.service;

import com.aivideoback.kwungjin.user.dto.RegisterRequest;
import com.aivideoback.kwungjin.user.dto.UserResponse;
import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Set<String> ALLOWED_PROFILE_IMAGES = Set.of(
            "blue", "purple", "orange", "green", "pink", "mono"
    );
    private final EmailVerificationService emailVerificationService;
    @Transactional
    public UserResponse register(RegisterRequest request) {

        if (!emailVerificationService.isRecentlyVerified(request.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다. 먼저 이메일 인증을 진행해 주세요.");
        }
        if (userRepository.existsByUserId(request.getUserId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .userId(request.getUserId())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .gender(request.getGender())
                .age(request.getAge())
                .email(request.getEmail())
                .profileImage(request.getProfileImage())
                .tokenCount(5L)
                .build();

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    public boolean isUserIdAvailable(String userId) {
        return !userRepository.existsByUserId(userId);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    /** 닉네임 변경 */
    @Transactional
    public UserResponse updateNickname(String userId, String newNickname) {
        String trimmed = newNickname == null ? null : newNickname.trim();

        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        }
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("닉네임은 최소 2글자 이상이어야 합니다.");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 같은 닉네임이면 그냥 현재 정보 리턴
        if (trimmed.equals(user.getNickname())) {
            return UserResponse.from(user);
        }

        // 다른 유저가 이미 쓰고 있으면 막기
        if (userRepository.existsByNickname(trimmed)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        user.setNickname(trimmed); // @PreUpdate 로 updatedAt 자동 세팅
        return UserResponse.from(user);
    }

    /** 비밀번호 변경 */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("현재 비밀번호와 새 비밀번호를 모두 입력해 주세요.");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("새 비밀번호는 최소 6자 이상이어야 합니다.");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
             throw new IllegalArgumentException("이전과 동일한 비밀번호는 사용할 수 없습니다.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    /** 프로필 이미지 변경 */
    @Transactional
    public UserResponse updateProfileImage(String userId, String profileImageKey) {

        if (profileImageKey == null || profileImageKey.isBlank()) {
            throw new IllegalArgumentException("프로필 이미지를 선택해 주세요.");
        }

        String trimmed = profileImageKey.trim();

        if (!ALLOWED_PROFILE_IMAGES.contains(trimmed)) {
            throw new IllegalArgumentException("허용되지 않는 프로필 이미지입니다.");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.setProfileImage(trimmed);   // blue / purple / ... 이런 키만 저장
        return UserResponse.from(user);
    }
}
