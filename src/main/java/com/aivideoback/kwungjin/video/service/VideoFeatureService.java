// src/main/java/com/aivideoback/kwungjin/video/service/VideoFeatureService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.ai.ImageTagService;
import com.aivideoback.kwungjin.video.dto.VideoAutoTagRequest;
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

            // GPT_IMAGE 것만 지우고 다시 저장 (DESKTOP_ML 은 유지)
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

        // 1) 영상 조회 (존재 여부 + TAG1~TAG5 업데이트용)
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        try {
            // 2) VIDEO_FEATURE_TABLE 에 DESKTOP_ML 기록 저장 (기존 로직 유지)
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("mainTag", req.getMainTag());
            jsonMap.put("subTags", req.getSubTags());
            jsonMap.put("presentTags", req.getPresentTags());
            jsonMap.put("allScores", req.getAllScores());
            jsonMap.put("frameCount", req.getFrameCount());

            String tagsJson = objectMapper.writeValueAsString(jsonMap);

            // 동일 source("DESKTOP_ML") 기록만 제거 후 새로 저장
            videoFeatureRepository.deleteByVideoNoAndSource(videoNo, "DESKTOP_ML");

            VideoFeature feature = VideoFeature.builder()
                    .videoNo(videoNo)
                    .source("DESKTOP_ML")
                    .frameTime(null)
                    .tagsJson(tagsJson)
                    .build();

            videoFeatureRepository.save(feature);

            // 3) mainTag + subTags + presentTags + allScores 순으로 TAG1~TAG5 채우기
            List<String> tagNames = new ArrayList<>();

            // (1) mainTag
            if (req.getMainTag() != null && req.getMainTag().getName() != null) {
                String name = normalizeTagName(req.getMainTag().getName());
                if (!name.isBlank()) {
                    tagNames.add(name);
                }
            }

            // (2) subTags
            if (req.getSubTags() != null) {
                for (VideoAutoTagRequest.TagScore ts : req.getSubTags()) {
                    if (ts == null || ts.getName() == null) continue;
                    String name = normalizeTagName(ts.getName());
                    if (name.isBlank()) continue;
                    if (!tagNames.contains(name)) {
                        tagNames.add(name);
                    }
                    if (tagNames.size() >= 5) break;
                }
            }

            // (3) presentTags (보조 – 중복 제거)
            if (tagNames.size() < 5 && req.getPresentTags() != null) {
                for (VideoAutoTagRequest.TagScore ts : req.getPresentTags()) {
                    if (ts == null || ts.getName() == null) continue;
                    String name = normalizeTagName(ts.getName());
                    if (name.isBlank()) continue;
                    if (!tagNames.contains(name)) {
                        tagNames.add(name);
                    }
                    if (tagNames.size() >= 5) break;
                }
            }

            // (4) allScores 상위에서 부족분 채우기
            if (tagNames.size() < 5 && req.getAllScores() != null && !req.getAllScores().isEmpty()) {
                List<Map.Entry<String, Double>> scoreList =
                        new ArrayList<>(req.getAllScores().entrySet());

                // 점수 기준 내림차순 정렬
                scoreList.sort((e1, e2) -> {
                    double v2 = (e2.getValue() != null ? e2.getValue() : 0.0);
                    double v1 = (e1.getValue() != null ? e1.getValue() : 0.0);
                    return Double.compare(v2, v1);
                });

                for (Map.Entry<String, Double> entry : scoreList) {
                    if (tagNames.size() >= 5) break;

                    String name = normalizeTagName(entry.getKey());
                    if (name.isBlank()) continue;
                    if (!tagNames.contains(name)) {
                        tagNames.add(name);
                    }
                }
            }

            // 5개를 넘으면 잘라내기 (안전용)
            if (tagNames.size() > 5) {
                tagNames = new ArrayList<>(tagNames.subList(0, 5));
            }

            // 5) VIDEO_TABLE.TAG1~TAG5에 반영
            video.setTag1(tagNames.size() > 0 ? tagNames.get(0) : null);
            video.setTag2(tagNames.size() > 1 ? tagNames.get(1) : null);
            video.setTag3(tagNames.size() > 2 ? tagNames.get(2) : null);
            video.setTag4(tagNames.size() > 3 ? tagNames.get(3) : null);
            video.setTag5(tagNames.size() > 4 ? tagNames.get(4) : null);

            String mainName = (req.getMainTag() != null ? req.getMainTag().getName() : null);
            log.info("데스크탑 ML 태그 저장 완료 videoNo={} mainTag={} tags={}",
                    videoNo, mainName, tagNames);

        } catch (Exception e) {
            log.error("데스크탑 ML 태그 저장 중 예외 videoNo={}", videoNo, e);
            throw new IllegalArgumentException("데스크탑 자동 태그 저장 실패");
        }
    }

    /** 태그 이름 통일용 (소문자 정리 등 필요 시) */
    private String normalizeTagName(String raw) {
        if (raw == null) return "";
        String name = raw.trim();
        // 여기서 소문자로 통일 (모델이 "Game" / "GAME" 섞어서 줄 수도 있으니까)
        return name.toLowerCase();
    }
}
