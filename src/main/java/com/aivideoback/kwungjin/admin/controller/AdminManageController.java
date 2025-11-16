// src/main/java/com/aivideoback/kwungjin/admin/controller/AdminManageController.java
package com.aivideoback.kwungjin.admin.controller;

import com.aivideoback.kwungjin.admin.dto.AdminUserSummaryDto;
import com.aivideoback.kwungjin.admin.dto.BlockedVideoDto;
import com.aivideoback.kwungjin.admin.service.AdminManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminManageController {

    private final AdminManageService adminManageService;

    @GetMapping("/users")
    public List<AdminUserSummaryDto> getUsers() {
        return adminManageService.getAllUsers();
    }

    @GetMapping("/videos/blocked")
    public List<BlockedVideoDto> getBlockedVideos() {
        return adminManageService.getBlockedVideos();
    }
}
