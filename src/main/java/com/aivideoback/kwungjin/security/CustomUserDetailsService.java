// src/main/java/com/aivideoback/kwungjin/security/CustomUserDetailsService.java
package com.aivideoback.kwungjin.security;

import com.aivideoback.kwungjin.admin.entity.Admin;
import com.aivideoback.kwungjin.admin.repository.AdminRepository;
import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1️⃣ 관리자 먼저 조회
        Admin admin = adminRepository.findByAdminId(username).orElse(null);

        if (admin != null) {
            String role = admin.getAdminRole(); // "ADMIN" 또는 "ROLE_ADMIN"
            String roleName = (role != null && role.startsWith("ROLE_"))
                    ? role
                    : "ROLE_" + role;

            boolean accountNonLocked = !"BLOCK".equalsIgnoreCase(admin.getAdminStatus());

            return org.springframework.security.core.userdetails.User
                    .withUsername(admin.getAdminId())
                    .password(admin.getAdminPassword())   // Admin 엔티티 필드명에 맞게
                    .authorities(roleName)
                    .accountLocked(!accountNonLocked)
                    .build();
        }

        // 2️⃣ 일반 사용자 조회
        User user = userRepository.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUserId())
                .password(user.getPassword())   // 🔹 여기! getPassword() 로 수정
                .roles("USER")                  // 내부적으로 ROLE_USER 로 변환
                .build();
    }
}
