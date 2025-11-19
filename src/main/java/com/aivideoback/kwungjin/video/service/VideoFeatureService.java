// src/main/java/com/aivideoback/kwungjin/video/service/VideoFeatureService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.ai.ImageTagService;
import com.aivideoback.kwungjin.video.dto.DesktopTagTargetDto;
import com.aivideoback.kwungjin.video.dto.VideoAutoTagRequest;
import com.aivideoback.kwungjin.video.dto.VideoAutoTagRequest.TagScore;
import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.entity.VideoFeature;
import com.aivideoback.kwungjin.video.repository.VideoFeatureRepository;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import com.aivideoback.kwungjin.video.util.VideoFrameExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFeatureService {

    private static final int MAX_AUTO_TAGS = 5;
    private static final String SOURCE_OLLAMA_DESKTOP = "OLLAMA_DESKTOP";

    private final VideoRepository videoRepository;
    private final VideoFeatureRepository videoFeatureRepository;

    // ✅ 데스크탑(Ollama)에서 보내준 태그 저장
    @Transactional
    public void saveAutoTagsFromDesktop(VideoAutoTagRequest req) {
        Long videoNo = req.getVideoNo();
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        // 같은 영상에 대해 이전 OLLAMA_DESKTOP 태그는 제거
        videoFeatureRepository.deleteByVideoNoAndSource(videoNo, SOURCE_OLLAMA_DESKTOP);

        List<String> tagNames = new ArrayList<>();

        if (req.getMainTag() != null && req.getMainTag().getName() != null) {
            tagNames.add(req.getMainTag().getName());
        }

        if (req.getSubTags() != null) {
            for (VideoAutoTagRequest.TagScore ts : req.getSubTags()) {
                String name = ts.getName();
                if (name == null || name.isBlank()) continue;
                if (!tagNames.contains(name)) tagNames.add(name);
                if (tagNames.size() >= MAX_AUTO_TAGS) break;
            }
        }

        if (req.getPresentTags() != null && tagNames.size() < MAX_AUTO_TAGS) {
            for (VideoAutoTagRequest.TagScore ts : req.getPresentTags()) {
                String name = ts.getName();
                if (name == null || name.isBlank()) continue;
                if (!tagNames.contains(name)) tagNames.add(name);
                if (tagNames.size() >= MAX_AUTO_TAGS) break;
            }
        }

        Map<String, Object> json = new HashMap<>();
        json.put("tags", tagNames);
        json.put("frameCount", req.getFrameCount());
        json.put("allScores", req.getAllScores());

        String tagsJson;
        try {
            tagsJson = new ObjectMapper().writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("tagsJson 직렬화 실패", e);
        }

        VideoFeature feature = VideoFeature.builder()
                .videoNo(videoNo)
                .frameTime(null)
                .source(SOURCE_OLLAMA_DESKTOP)
                .tagsJson(tagsJson)
                .build();

        videoFeatureRepository.save(feature);

        // ✅ VIDEO_TABLE 의 TAG1~TAG5 도 같이 채워주기 (검색/정렬용)
        if (!tagNames.isEmpty()) {
            if (tagNames.size() > 0) video.setTag1(tagNames.get(0));
            if (tagNames.size() > 1) video.setTag2(tagNames.get(1));
            if (tagNames.size() > 2) video.setTag3(tagNames.get(2));
            if (tagNames.size() > 3) video.setTag4(tagNames.get(3));
            if (tagNames.size() > 4) video.setTag5(tagNames.get(4));
        }
    }

    // ✅ 데스크탑이 가져갈 “아직 태깅 안 된 승인 영상” 목록
    @Transactional(readOnly = true)
    public List<DesktopTagTargetDto> getPendingVideosForDesktop(int limit) {

        List<Video> candidates =
                videoRepository.findTop200ByIsBlockedAndReviewStatusOrderByCreatedAtDesc("N", "A");

        List<DesktopTagTargetDto> result = new ArrayList<>();

        for (Video v : candidates) {
            if (result.size() >= limit) break;

            Long videoNo = v.getVideoNo();
            // 이미 OLLAMA_DESKTOP 태그가 있으면 패스
            if (videoFeatureRepository.existsByVideoNoAndSource(videoNo, SOURCE_OLLAMA_DESKTOP)) {
                continue;
            }

            result.add(
                    DesktopTagTargetDto.builder()
                            .videoNo(videoNo)
                            .title(v.getTitle())
                            .createdAt(v.getCreatedAt())
                            .uploadDate(v.getUploadDate())
                            .build()
            );
        }

        return result;
    }

    // ❌ 기존 GPT 연동용 extractAndSaveFeatures(videoNo)는 더 이상 사용 안 하면 삭제 or @Deprecated
}

