package com.aivideoback.kwungjin.user.controller;

import com.aivideoback.kwungjin.user.dto.*;
import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import com.aivideoback.kwungjin.user.service.AuthService;
import com.aivideoback.kwungjin.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse user = userService.register(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // 로그인 상태 체크 + 내 정보
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal UserDetails principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByUserId(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return ResponseEntity.ok(UserResponse.from(user));
    }
    @GetMapping("/check-userid")
    public ResponseEntity<DuplicateCheckResponse> checkUserId(
            @RequestParam String userId
    ) {
        boolean available = userService.isUserIdAvailable(userId);
        return ResponseEntity.ok(DuplicateCheckResponse.of(available, "아이디"));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<DuplicateCheckResponse> checkNickname(
            @RequestParam String nickname
    ) {
        boolean available = userService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(DuplicateCheckResponse.of(available, "닉네임"));
    }

    @GetMapping("/check-email")
    public ResponseEntity<DuplicateCheckResponse> checkEmail(
            @RequestParam String email
    ) {
        boolean available = userService.isEmailAvailable(email);
        return ResponseEntity.ok(DuplicateCheckResponse.of(available, "이메일"));
    }

    /** 닉네임 변경 (프론트: PATCH /api/auth/nickname) */
    // 닉네임 변경 (JWT 필요)
    @PatchMapping("/nickname")
    public ResponseEntity<UserResponse> updateNickname(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String userId = principal.getUsername();
        UserResponse updated = userService.updateNickname(userId, request.getNickname());
        return ResponseEntity.ok(updated);
    }

    // 비밀번호 변경 (JWT 필요)
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String userId = principal.getUsername();
        userService.changePassword(
                userId,
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/profile-image")
    public ResponseEntity<UserResponse> updateProfileImage(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ProfileImageUpdateRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String userId = principal.getUsername();
        UserResponse updated = userService.updateProfileImage(userId, request.getProfileImage());
        return ResponseEntity.ok(updated);
    }
}
