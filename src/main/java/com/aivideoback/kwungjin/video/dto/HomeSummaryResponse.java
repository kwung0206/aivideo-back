package com.aivideoback.kwungjin.video.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeSummaryResponse {

    private long totalCount;

    private SimpleVideoDto topLiked;
    private SimpleVideoDto topViewed;
    private SimpleVideoDto topDisliked;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleVideoDto {
        private Long videoNo;
        private String title;
        private String description;
        private String thumbnailUrl;
        private String videoUrl;
        private long likeCount;
        private long dislikeCount;
        private long viewCount;
        private String uploaderNickname;
        private LocalDateTime createdAt;
        private List<String> tags;
    }
}
