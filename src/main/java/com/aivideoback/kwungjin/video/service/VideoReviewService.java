// src/main/java/com/aivideoback/kwungjin/video/service/VideoReviewService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.videointelligence.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoReviewService {

    private final VideoRepository videoRepository;
    // ✅ 이제 여기서는 태그 추출을 안 할 거라면 주석 처리 / 삭제
    // private final VideoFeatureService videoFeatureService;

    @Async
    @Transactional
    public void reviewVideoAsync(Long videoNo) {

        log.info("영상 자동 심사 시작 videoNo={}", videoNo);

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        byte[] fileData;
        try {
            Path path = Paths.get(video.getFilePath());
            fileData = Files.readAllBytes(path);
        } catch (Exception e) {
            log.error("영상 파일 읽기 실패, 심사 불가 videoNo={}", videoNo, e);
            video.setReviewStatus("H");
            video.setIsBlocked("Y");
            videoRepository.save(video);   // ✅ 명시적으로 저장
            return;
        }

        boolean harmful = false;

        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {

            AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                    .setInputContent(ByteString.copyFrom(fileData))
                    .addFeatures(Feature.EXPLICIT_CONTENT_DETECTION)
                    .build();

            OperationFuture<AnnotateVideoResponse, AnnotateVideoProgress> future =
                    client.annotateVideoAsync(request);

            AnnotateVideoResponse response = future.get(5, TimeUnit.MINUTES);

            for (VideoAnnotationResults results : response.getAnnotationResultsList()) {
                ExplicitContentAnnotation explicitAnnotation = results.getExplicitAnnotation();
                for (ExplicitContentFrame frame : explicitAnnotation.getFramesList()) {
                    Likelihood likelihood = frame.getPornographyLikelihood();
                    if (likelihood == Likelihood.LIKELY || likelihood == Likelihood.VERY_LIKELY) {
                        harmful = true;
                        break;
                    }
                }
                if (harmful) break;
            }

        } catch (Exception e) {
            log.error("영상 자동 심사 중 예외 발생 videoNo={}", videoNo, e);
            harmful = true;
        }

        if (harmful) {
            video.setReviewStatus("H");
            video.setIsBlocked("Y");
            log.info("영상 자동 심사 결과: 보류(H) videoNo={}", videoNo);
        } else {
            video.setReviewStatus("A");
            video.setIsBlocked("N");
            log.info("영상 자동 심사 결과: 승인(A) videoNo={}", videoNo);
        }

        // ✅ 무조건 저장해서 DB에 반영
        videoRepository.save(video);

        // ❌ 이제 여기선 태그 추출 안 함 (Ollama 데스크탑 워커가 맡음)
        /*
        if (!harmful) {
            try {
                videoFeatureService.extractAndSaveFeatures(videoNo);
            } catch (Exception e) {
                log.warn("영상 특징 추출 스케줄링 실패 videoNo={}", videoNo, e);
            }
        }
        */
    }
}

