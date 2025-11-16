// src/main/java/com/aivideoback/kwungjin/video/controller/VideoController.java
package com.aivideoback.kwungjin.video.controller;

import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.dto.VideoSummaryDto;
import com.aivideoback.kwungjin.video.dto.VideoUpdateRequest;
import com.aivideoback.kwungjin.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aivideoback.kwungjin.video.dto.VideoReactionResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    // ✅ 공개 갤러리용: 승인된 영상 페이지네이션 + 태그 필터
    // VideoController.java

    @GetMapping("/public")
    public ResponseEntity<Page<VideoSummaryDto>> getPublicVideos(
            @AuthenticationPrincipal(expression = "username") String userId, // ✅ 추가
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
                videoService.getPublicVideos(keyword, tagList, page, size, userId); // ✅ userId 전달

        return ResponseEntity.ok(result);
    }


    // 🎥 영상 스트리밍 (모달에서 재생용)
    @GetMapping("/{videoNo}/stream")
    public ResponseEntity<Resource> streamVideo(@PathVariable Long videoNo) {
        VideoResponse v = videoService.getVideoForStream(videoNo);

        ByteArrayResource resource = new ByteArrayResource(v.getFileData());
        String encodedName = URLEncoder.encode(v.getFileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(v.getContentType()))
                .contentLength(v.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedName + "\"")
                .body(resource);
    }

    // 🗑 영상 삭제
    @DeleteMapping("/{videoNo}")
    public ResponseEntity<Void> deleteVideo(
            @AuthenticationPrincipal(expression = "username") String userId,
            @PathVariable Long videoNo
    ) {
        videoService.deleteMyVideo(userId, videoNo);
        return ResponseEntity.noContent().build();
    }

    // ✏ 내 영상 제목 수정
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
            @RequestParam("action") String action   // LIKE / DISLIKE
    ) {
        VideoReactionResponse resp = videoService.toggleReaction(userId, videoNo, action);
        return ResponseEntity.ok(resp);
    }
}
