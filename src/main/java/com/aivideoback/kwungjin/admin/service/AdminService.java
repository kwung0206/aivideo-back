// src/main/java/com/aivideoback/kwungjin/admin/service/AdminService.java
package com.aivideoback.kwungjin.admin.service;

import com.aivideoback.kwungjin.admin.dto.AdminLoginRequest;
import com.aivideoback.kwungjin.admin.dto.AdminLoginResponse;
import com.aivideoback.kwungjin.admin.entity.Admin;
import com.aivideoback.kwungjin.admin.repository.AdminRepository;
import com.aivideoback.kwungjin.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {

        // username == adminId
        Admin admin = adminRepository.findByAdminId(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다."));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), admin.getAdminPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // DB에 저장된 ROLE (예: "ADMIN" 또는 "ROLE_ADMIN")
        String role = admin.getAdminRole();

        // JwtTokenProvider가 기대하는 타입: Collection<? extends GrantedAuthority>
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role
                )
        );

        String token = jwtTokenProvider.createToken(admin.getAdminId(), authorities);

        // 마지막 로그인 시간 갱신
        admin.setLastLoginAt(LocalDateTime.now());

        return AdminLoginResponse.builder()
                .token(token)
                .adminId(admin.getAdminId())
                .adminName(admin.getAdminName())
                .role(role)
                .build();
    }

}
