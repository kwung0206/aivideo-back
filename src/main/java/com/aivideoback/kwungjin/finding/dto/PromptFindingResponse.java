// src/main/java/com/aivideoback/kwungjin/finding/dto/PromptFindingResponse.java
package com.aivideoback.kwungjin.finding.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PromptFindingResponse {

    private final String originalPrompt;
    private final String intentSummary;
    private final List<String> predictedTags;
    private final List<VideoMatchDto> videos;

    @Getter
    @Builder
    public static class VideoMatchDto {
        private final Long videoNo;
        private final String title;
        private final String description;
        private final long views;
        private final long likes;
        private final long dislikes;
        private final LocalDateTime createdAt;
        private final Long durationSec;
        private final List<String> tags;   // 🔹 프론트에 내려줄 태그 리스트

        /** 0.0 ~ 1.0 사이 매치 점수 */
        private final double matchScore;

        /** HIGH / MEDIUM / LOW */
        private final String matchLevel;
    }
}
