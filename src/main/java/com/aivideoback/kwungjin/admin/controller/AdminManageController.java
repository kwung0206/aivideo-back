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

    /** 전체 유저 조회 */
    @GetMapping("/users")
    public List<AdminUserSummaryDto> getUsers() {
        return adminManageService.getAllUsers();
    }

    /** 차단된 영상 목록 조회 */
    @GetMapping("/videos/blocked")
    public List<BlockedVideoDto> getBlockedVideos() {
        return adminManageService.getBlockedVideos();
    }

    /** 차단 영상 승인(차단 해제) - ⭐ POST 사용 */
    @PostMapping("/videos/{videoNo}/approve")
    public void approveVideo(@PathVariable Long videoNo) {
        adminManageService.approveVideo(videoNo);
    }

    /** 영상 완전 삭제 */
    @DeleteMapping("/videos/{videoNo}")
    public void deleteVideo(@PathVariable Long videoNo) {
        adminManageService.deleteVideo(videoNo);
    }
}
