// src/main/java/com/aivideoback/kwungjin/admin/controller/AdminAuthController.java
package com.aivideoback.kwungjin.admin.controller;

import com.aivideoback.kwungjin.admin.dto.AdminLoginRequest;
import com.aivideoback.kwungjin.admin.dto.AdminLoginResponse;
import com.aivideoback.kwungjin.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminService adminService;

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        AdminLoginResponse resp = adminService.login(request);
        return ResponseEntity.ok(resp);
    }
}
