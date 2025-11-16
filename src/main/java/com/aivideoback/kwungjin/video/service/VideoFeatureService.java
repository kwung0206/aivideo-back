// src/main/java/com/aivideoback/kwungjin/video/service/VideoFeatureService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.ai.ImageTagService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFeatureService {

    private final VideoRepository videoRepository;
    private final VideoFeatureRepository videoFeatureRepository;
    private final ImageTagService imageTagService;
    private final ObjectMapper objectMapper;

    /**
     * 승인된(A) 영상에 대해
     * 파일 시스템에서 영상 파일을 읽어와서
     * 프레임 추출 → 태그 생성 → VIDEO_FEATURE_TABLE 저장.
     */
    @Async
    @Transactional
    public void extractAndSaveFeatures(Long videoNo) {
        log.info("영상 특징 추출 시작 videoNo={}", videoNo);

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        byte[] fileData;
        try {
            Path path = Paths.get(video.getFilePath());
            if (!Files.exists(path)) {
                log.warn("영상 파일이 존재하지 않음. 특징 추출 불가 path={} videoNo={}", path, videoNo);
                return;
            }
            fileData = Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("영상 파일 읽기 실패. 특징 추출 불가 videoNo={}", videoNo, e);
            return;
        }

        // 1) ffmpeg로 대표 프레임 여러 장 추출
        List<byte[]> frameBytesList = new ArrayList<>();
        try {
            List<File> frameFiles = VideoFrameExtractor.extractThumbnailFrames(fileData);

            if (frameFiles == null || frameFiles.isEmpty()) {
                log.warn("프레임 추출 실패 (frames empty) videoNo={}", videoNo);
                return;
            }

            for (File f : frameFiles) {
                try {
                    frameBytesList.add(Files.readAllBytes(f.toPath()));
                } catch (IOException e) {
                    log.warn("프레임 파일 읽기 실패: {} videoNo={}", f.getAbsolutePath(), videoNo, e);
                }
            }

        } catch (IOException e) {
            log.error("ffmpeg 프레임 추출 중 IO 예외 발생 videoNo={}", videoNo, e);
            return;
        } catch (InterruptedException e) {
            log.error("ffmpeg 프레임 추출 중 인터럽트 발생 videoNo={}", videoNo, e);
            Thread.currentThread().interrupt();
            return;
        }

        if (frameBytesList.isEmpty()) {
            log.warn("프레임 바이트 리스트가 비어있음 videoNo={}", videoNo);
            return;
        }

        // 2) OpenAI로 이미지 태그 추출
        List<String> tags = imageTagService.extractTagsFromFrames(frameBytesList);
        if (tags == null || tags.isEmpty()) {
            log.warn("GPT가 태그를 반환하지 않음 videoNo={}", videoNo);
            return;
        }

        // 3) JSON 문자열로 직렬화 후 DB 저장
        try {
            String tagsJson = objectMapper.writeValueAsString(Map.of("tags", tags));

            videoFeatureRepository.deleteByVideoNo(videoNo);

            VideoFeature feature = VideoFeature.builder()
                    .videoNo(videoNo)
                    .source("GPT_IMAGE")
                    .frameTime(null)
                    .tagsJson(tagsJson)
                    .build();

            videoFeatureRepository.save(feature);
            log.info("영상 특징 저장 완료 videoNo={} tagsCount={}", videoNo, tags.size());

        } catch (Exception e) {
            log.error("영상 특징 저장 중 예외 발생 videoNo={}", videoNo, e);
        }
    }
}
