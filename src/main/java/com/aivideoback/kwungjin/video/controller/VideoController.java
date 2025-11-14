// src/main/java/com/aivideoback/kwungjin/video/controller/VideoController.java
package com.aivideoback.kwungjin.video.controller;

import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ResponseEntity<VideoResponse> uploadVideo(
            // ✅ principal에서 username(=우리 USER_ID) 꺼내기
            @AuthenticationPrincipal(expression = "username") String userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) throws IOException {

        VideoResponse resp = videoService.uploadVideo(userId, title, description, tags, file);
        return ResponseEntity.ok(resp);
    }
}
