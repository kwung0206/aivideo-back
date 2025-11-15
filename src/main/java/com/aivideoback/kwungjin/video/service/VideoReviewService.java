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

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoReviewService {

    private final VideoRepository videoRepository;

    /**
     * 업로드 직후 비동기로 호출되는 자동 심사 메서드.
     * - Google Video Intelligence API의 Explicit Content Detection 사용
     * - 결과에 따라 REVIEW_STATUS / IS_BLOCKED 갱신
     */
    @Async
    @Transactional
    public void reviewVideoAsync(Long videoNo, byte[] fileData) {

        log.info("영상 자동 심사 시작 videoNo={}", videoNo);

        boolean harmful = false;

        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {

            AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                    // ✅ BLOB 내용을 그대로 전송 (운영에서는 GCS URL 사용 권장)
                    .setInputContent(ByteString.copyFrom(fileData))
                    .addFeatures(Feature.EXPLICIT_CONTENT_DETECTION)
                    .build();

            OperationFuture<AnnotateVideoResponse, AnnotateVideoProgress> future =
                    client.annotateVideoAsync(request);

            // 영상 길이에 따라 이 타임아웃은 조절 가능
            AnnotateVideoResponse response = future.get(5, TimeUnit.MINUTES);

            for (VideoAnnotationResults results : response.getAnnotationResultsList()) {
                ExplicitContentAnnotation explicitAnnotation = results.getExplicitAnnotation();
                for (ExplicitContentFrame frame : explicitAnnotation.getFramesList()) {
                    Likelihood likelihood = frame.getPornographyLikelihood();
                    // LIKELY 이상이면 유해한 영상으로 판단
                    if (likelihood == Likelihood.LIKELY || likelihood == Likelihood.VERY_LIKELY) {
                        harmful = true;
                        break;
                    }
                }
                if (harmful) break;
            }

        } catch (Exception e) {
            // API 실패 시 기본적으로 보류(H) 처리 or 계속 P로 둘지 정책에 따라 결정
            log.error("영상 자동 심사 중 예외 발생 videoNo={}", videoNo, e);
            harmful = true; // 안전하게 막고 시작
        }

        // DB 상태 갱신
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        if (harmful) {
            video.setReviewStatus("H");   // 보류
            video.setIsBlocked("Y");      // 차단
            log.info("영상 자동 심사 결과: 보류(H) videoNo={}", videoNo);
        } else {
            video.setReviewStatus("A");   // 승인
            video.setIsBlocked("N");
            log.info("영상 자동 심사 결과: 승인(A) videoNo={}", videoNo);
        }
    }
}
