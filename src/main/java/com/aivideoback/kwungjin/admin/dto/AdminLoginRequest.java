// src/main/java/com/aivideoback/kwungjin/admin/dto/AdminLoginRequest.java
package com.aivideoback.kwungjin.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminLoginRequest {
    private String username;   // 프론트에서 보내는 값 (실제 adminId)
    private String password;
}
