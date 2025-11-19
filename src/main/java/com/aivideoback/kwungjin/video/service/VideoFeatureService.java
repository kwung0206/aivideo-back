// src/main/java/com/aivideoback/kwungjin/video/service/VideoFeatureService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.ai.ImageTagService;
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
     * 프레임 추출 → GPT 이미지 태그 생성 → VIDEO_FEATURE_TABLE 저장.
     * SOURCE = "GPT_IMAGE"
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

        // 3) JSON 문자열로 직렬화 후 DB 저장 (SOURCE = GPT_IMAGE)
        try {
            String tagsJson = objectMapper.writeValueAsString(Map.of("tags", tags));

            // 🔥 이제는 GPT_IMAGE 것만 지우고 다시 저장 (DESKTOP_ML 은 유지)
            videoFeatureRepository.deleteByVideoNoAndSource(videoNo, "GPT_IMAGE");

            VideoFeature feature = VideoFeature.builder()
                    .videoNo(videoNo)
                    .source("GPT_IMAGE")
                    .frameTime(null)
                    .tagsJson(tagsJson)
                    .build();

            videoFeatureRepository.save(feature);
            log.info("영상 특징(GPT_IMAGE) 저장 완료 videoNo={} tagsCount={}", videoNo, tags.size());

        } catch (Exception e) {
            log.error("영상 특징(GPT_IMAGE) 저장 중 예외 발생 videoNo={}", videoNo, e);
        }
    }

    /**
     * 데스크탑 멀티라벨 모델에서 보내주는 자동 태그 저장 + VIDEO_TABLE 태그 업데이트
     * SOURCE = "DESKTOP_ML"
     */
    @Transactional
    public void saveAutoTagsFromDesktop(VideoAutoTagRequest req) {
        Long videoNo = req.getVideoNo();
        if (videoNo == null) {
            throw new IllegalArgumentException("videoNo는 필수입니다.");
        }

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        // 1) mainTag + subTags 기반으로 상위 태그 리스트 구성
        List<String> collected = new ArrayList<>();

        TagScore main = req.getMainTag();
        if (main != null && main.getName() != null) {
            String name = main.getName().trim();
            if (!name.isEmpty()) {
                collected.add(name);
            }
        }

        if (req.getSubTags() != null) {
            for (TagScore t : req.getSubTags()) {
                if (t == null || t.getName() == null) continue;
                String name = t.getName().trim();
                if (name.isEmpty()) continue;
                collected.add(name);
            }
        }

        // 2) 중복 제거 + 최대 5개까지만 사용
        List<String> distinctTags = collected.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(5)
                .toList();

        // 3) VIDEO_TABLE 의 TAG1~TAG5 업데이트
        video.setTag1(distinctTags.size() > 0 ? distinctTags.get(0) : null);
        video.setTag2(distinctTags.size() > 1 ? distinctTags.get(1) : null);
        video.setTag3(distinctTags.size() > 2 ? distinctTags.get(2) : null);
        video.setTag4(distinctTags.size() > 3 ? distinctTags.get(3) : null);
        video.setTag5(distinctTags.size() > 4 ? distinctTags.get(4) : null);

        // JPA @Transactional 이라 별도 save() 안 해도 flush 될 것

        // 4) VIDEO_FEATURE_TABLE 에도 전체 결과 JSON으로 기록 (SOURCE = DESKTOP_ML)
        try {
            Map<String, Object> jsonMap = new LinkedHashMap<>();
            jsonMap.put("mainTag", req.getMainTag());
            jsonMap.put("subTags", req.getSubTags());
            jsonMap.put("presentTags", req.getPresentTags());
            jsonMap.put("allScores", req.getAllScores());
            jsonMap.put("frameCount", req.getFrameCount());

            String tagsJson = objectMapper.writeValueAsString(jsonMap);

            // DESKTOP_ML 것만 지우고 다시 저장
            videoFeatureRepository.deleteByVideoNoAndSource(videoNo, "DESKTOP_ML");

            VideoFeature feature = VideoFeature.builder()
                    .videoNo(videoNo)
                    .source("DESKTOP_ML")
                    .frameTime(null)
                    .tagsJson(tagsJson)
                    .build();

            videoFeatureRepository.save(feature);

            String mainName = (main != null ? main.getName() : null);
            log.info("데스크탑 ML 태그 저장 & VIDEO_TABLE 태그 업데이트 완료 videoNo={} mainTag={} tags={}",
                    videoNo, mainName, distinctTags);

        } catch (Exception e) {
            log.error("데스크탑 ML 태그 저장 중 예외 videoNo={}", videoNo, e);
            throw new IllegalArgumentException("데스크탑 자동 태그 저장 실패");
        }
    }
}
