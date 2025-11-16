// src/main/java/com/aivideoback/kwungjin/admin/dto/AdminLoginResponse.java
package com.aivideoback.kwungjin.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AdminLoginResponse {

    private String token;
    private String adminId;
    private String adminName;
    private String role;       // 예: "ADMIN" 또는 "ROLE_ADMIN"
}
