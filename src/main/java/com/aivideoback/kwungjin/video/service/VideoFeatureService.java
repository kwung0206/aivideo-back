// src/main/java/com/aivideoback/kwungjin/video/service/VideoFeatureService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.ai.ImageTagService;
import com.aivideoback.kwungjin.video.dto.DesktopTagTargetDto;
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
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFeatureService {

    private static final int MAX_TAGS = 5;

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
        List<String> gptTags = imageTagService.extractTagsFromFrames(frameBytesList);
        if (gptTags == null || gptTags.isEmpty()) {
            log.warn("GPT가 태그를 반환하지 않음 videoNo={}", videoNo);
            return;
        }

        // 태그 정규화 (소문자, 공백 제거, 중복 제거)
        List<String> normalizedGptTags = gptTags.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeTagName)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(MAX_TAGS)
                .toList();

        // 3) JSON 문자열로 직렬화 후 DB 저장 (SOURCE = GPT_IMAGE)
        try {
            String tagsJson = objectMapper.writeValueAsString(Map.of("tags", gptTags));

            // GPT_IMAGE 것만 지우고 다시 저장 (DESKTOP_ML 은 유지)
            videoFeatureRepository.deleteByVideoNoAndSource(videoNo, "GPT_IMAGE");

            VideoFeature feature = VideoFeature.builder()
                    .videoNo(videoNo)
                    .source("GPT_IMAGE")
                    .frameTime(null)
                    .tagsJson(tagsJson)
                    .build();

            videoFeatureRepository.save(feature);
            log.info("영상 특징(GPT_IMAGE) 저장 완료 videoNo={} tagsCount={}", videoNo, gptTags.size());

            // 🔥 TAG1~TAG5 가 아직 비어 있다면 GPT 태그로 기본값 채워주기
            applyTagsIfEmpty(video, normalizedGptTags, "GPT_IMAGE");

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
                    if (tagNames.size() >= MAX_TAGS) break;
                }
            }

            // (3) presentTags (보조 – 중복 제거)
            if (tagNames.size() < MAX_TAGS && req.getPresentTags() != null) {
                for (VideoAutoTagRequest.TagScore ts : req.getPresentTags()) {
                    if (ts == null || ts.getName() == null) continue;
                    String name = normalizeTagName(ts.getName());
                    if (name.isBlank()) continue;
                    if (!tagNames.contains(name)) {
                        tagNames.add(name);
                    }
                    if (tagNames.size() >= MAX_TAGS) break;
                }
            }

            // (4) allScores 상위에서 부족분 채우기
            if (tagNames.size() < MAX_TAGS && req.getAllScores() != null && !req.getAllScores().isEmpty()) {
                List<Map.Entry<String, Double>> sortedEntries = req.getAllScores().entrySet()
                        .stream()
                        .sorted((e1, e2) -> {
                            double v2 = (e2.getValue() != null ? e2.getValue() : 0.0);
                            double v1 = (e1.getValue() != null ? e1.getValue() : 0.0);
                            return Double.compare(v2, v1); // 내림차순
                        })
                        .toList();

                for (Map.Entry<String, Double> entry : sortedEntries) {
                    if (tagNames.size() >= MAX_TAGS) break;
                    String name = normalizeTagName(entry.getKey());
                    if (name.isBlank()) continue;
                    if (!tagNames.contains(name)) {
                        tagNames.add(name);
                    }
                }
            }

            // 5개를 넘으면 자르기
            List<String> limitedTags =
                    (tagNames.size() > MAX_TAGS) ? tagNames.subList(0, MAX_TAGS) : tagNames;

            // 5) VIDEO_TABLE.TAG1~TAG5에 반영 (DESKTOP_ML 은 GPT_IMAGE 보다 우선순위 높게 항상 덮어씀)
            video.setTag1(limitedTags.size() > 0 ? limitedTags.get(0) : null);
            video.setTag2(limitedTags.size() > 1 ? limitedTags.get(1) : null);
            video.setTag3(limitedTags.size() > 2 ? limitedTags.get(2) : null);
            video.setTag4(limitedTags.size() > 3 ? limitedTags.get(3) : null);
            video.setTag5(limitedTags.size() > 4 ? limitedTags.get(4) : null);

            String mainName = (req.getMainTag() != null ? req.getMainTag().getName() : null);
            log.info("데스크탑 ML 태그 저장 완료 videoNo={} mainTag={} tags={}",
                    videoNo, mainName, limitedTags);

        } catch (Exception e) {
            log.error("데스크탑 ML 태그 저장 중 예외 videoNo={}", videoNo, e);
            throw new IllegalArgumentException("데스크탑 자동 태그 저장 실패");
        }
    }

    /** 태그 이름 통일용 (소문자 정리 등 필요 시) */
    private String normalizeTagName(String raw) {
        if (raw == null) return "";
        String name = raw.trim();
        // 영어는 소문자로 통일 (한글은 그대로)
        return name.toLowerCase();
    }

    /**
     * 현재 VIDEO_TABLE.TAG1~TAG5 가 전부 비어 있을 때만
     * 주어진 태그 리스트로 기본값을 채운다.
     * (GPT_IMAGE → 기본, DESKTOP_ML → 항상 덮어쓰므로 여기선 GPT 전용으로 사용)
     */
    private void applyTagsIfEmpty(Video video, List<String> tags, String sourceLabel) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        boolean hasAnyTag = Stream.of(
                        video.getTag1(),
                        video.getTag2(),
                        video.getTag3(),
                        video.getTag4(),
                        video.getTag5()
                )
                .anyMatch(t -> t != null && !t.isBlank());

        if (hasAnyTag) {
            log.debug("영상 {} 은 이미 TAG1~TAG5 가 존재하므로 {} 태그로는 덮어쓰지 않음", video.getVideoNo(), sourceLabel);
            return;
        }

        List<String> limited =
                tags.size() > MAX_TAGS ? tags.subList(0, MAX_TAGS) : tags;

        video.setTag1(limited.size() > 0 ? limited.get(0) : null);
        video.setTag2(limited.size() > 1 ? limited.get(1) : null);
        video.setTag3(limited.size() > 2 ? limited.get(2) : null);
        video.setTag4(limited.size() > 3 ? limited.get(3) : null);
        video.setTag5(limited.size() > 4 ? limited.get(4) : null);

        log.info("영상 {} TAG1~TAG5 를 {} 태그로 기본 세팅: {}", video.getVideoNo(), sourceLabel, limited);
    }

    @Transactional(readOnly = true)
    public List<DesktopTagTargetDto> getPendingVideosForDesktop(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50)); // 1~50 사이로 제한

        // 최근 생성된 공개(A, N) 영상 최대 200개 후보
        List<Video> candidates =
                videoRepository.findTop200ByIsBlockedAndReviewStatusOrderByCreatedAtDesc("N", "A");

        List<DesktopTagTargetDto> result = new ArrayList<>();

        for (Video v : candidates) {
            // 이미 DESKTOP_ML 태그가 있는 영상은 건너뛰기
            if (!videoFeatureRepository.existsByVideoNoAndSource(v.getVideoNo(), "DESKTOP_ML")) {
                result.add(
                        DesktopTagTargetDto.builder()
                                .videoNo(v.getVideoNo())
                                .title(v.getTitle())
                                .createdAt(v.getCreatedAt())
                                .uploadDate(v.getUploadDate())
                                .build()
                );
                if (result.size() >= safeLimit) {
                    break;
                }
            }
        }

        return result;
    }
}
