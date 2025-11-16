// src/main/java/com/aivideoback/kwungjin/video/controller/VideoController.java
package com.aivideoback.kwungjin.video.controller;

import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.dto.VideoSummaryDto;
import com.aivideoback.kwungjin.video.dto.VideoUpdateRequest;
import com.aivideoback.kwungjin.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.aivideoback.kwungjin.video.dto.HomeSummaryResponse;
import com.aivideoback.kwungjin.video.dto.VideoReactionResponse;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ResponseEntity<VideoResponse> uploadVideo(
            @AuthenticationPrincipal(expression = "username") String userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) throws IOException {

        VideoResponse resp = videoService.uploadVideo(userId, title, description, tags, file);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/my")
    public List<VideoSummaryDto> getMyVideos(
            @AuthenticationPrincipal(expression = "username") String userId
    ) {
        return videoService.getMyVideosByUserId(userId);
    }

    @GetMapping("/public")
    public ResponseEntity<Page<VideoSummaryDto>> getPublicVideos(
            @AuthenticationPrincipal(expression = "username") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "36") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tags
    ) {
        List<String> tagList = Collections.emptyList();
        if (tags != null && !tags.isBlank()) {
            tagList = Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        Page<VideoSummaryDto> result =
                videoService.getPublicVideos(keyword, tagList, page, size, userId);

        return ResponseEntity.ok(result);
    }

    // 🎥 영상 스트리밍 (파일 시스템에서 직접)
    @GetMapping("/{videoNo}/stream")
    public ResponseEntity<Resource> streamVideo(@PathVariable Long videoNo) {
        VideoResponse v = videoService.getVideoForStream(videoNo);

        File file = new File(v.getFilePath());
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String encodedName = URLEncoder.encode(v.getFileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(v.getContentType()))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedName + "\"")
                .body(resource);
    }

    @DeleteMapping("/{videoNo}")
    public ResponseEntity<Void> deleteVideo(
            @AuthenticationPrincipal(expression = "username") String userId,
            @PathVariable Long videoNo
    ) {
        videoService.deleteMyVideo(userId, videoNo);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{videoNo}")
    public ResponseEntity<VideoSummaryDto> updateMyVideo(
            @AuthenticationPrincipal(expression = "username") String userId,
            @PathVariable Long videoNo,
            @RequestBody VideoUpdateRequest request
    ) {
        VideoSummaryDto dto = videoService.updateMyVideo(userId, videoNo, request);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{videoNo}/reaction")
    public ResponseEntity<VideoReactionResponse> toggleReaction(
            @AuthenticationPrincipal(expression = "username") String userId,
            @PathVariable Long videoNo,
            @RequestParam("action") String action
    ) {
        VideoReactionResponse resp = videoService.toggleReaction(userId, videoNo, action);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/home-summary")
    public ResponseEntity<HomeSummaryResponse> getHomeSummary() {
        return ResponseEntity.ok(videoService.getHomeSummary());
    }

    @PostMapping("/{videoNo}/view")
    public ResponseEntity<Map<String, Long>> increaseView(@PathVariable Long videoNo) {
        long viewCount = videoService.increaseViewCount(videoNo);
        return ResponseEntity.ok(Map.of("viewCount", viewCount));
    }
}
