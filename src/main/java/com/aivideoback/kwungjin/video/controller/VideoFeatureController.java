// src/main/java/com/aivideoback/kwungjin/video/controller/VideoFeatureController.java
package com.aivideoback.kwungjin.video.controller;

import com.aivideoback.kwungjin.video.dto.VideoAutoTagRequest;
import com.aivideoback.kwungjin.video.dto.DesktopTagTargetDto;
import com.aivideoback.kwungjin.video.service.VideoFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/features")
@RequiredArgsConstructor
@Slf4j
public class VideoFeatureController {

    private final VideoFeatureService videoFeatureService;

    /**
     * 데스크탑 워커에서 보내주는 자동 태그 저장용 엔드포인트
     */
    @PostMapping("/auto-tags")
    public ResponseEntity<Void> receiveAutoTags(
            @RequestBody VideoAutoTagRequest request
    ) {
        log.info("데스크탑 자동 태그 수신 videoNo={}", request.getVideoNo());
        videoFeatureService.saveAutoTagsFromDesktop(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 데스크탑 ML 워커가 주기적으로 가져가는
     * "아직 DESKTOP_ML 태그가 없는 승인된 영상" 목록
     */
    @GetMapping("/pending-desktop")
    public ResponseEntity<List<DesktopTagTargetDto>> getPendingDesktopTargets(
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(
                videoFeatureService.getPendingVideosForDesktop(limit)
        );
    }
}
