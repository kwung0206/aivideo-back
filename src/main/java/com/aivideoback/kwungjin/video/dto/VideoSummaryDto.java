// src/main/java/com/aivideoback/kwungjin/video/dto/VideoSummaryDto.java
package com.aivideoback.kwungjin.video.dto;

import com.aivideoback.kwungjin.video.entity.Video;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 프로필 화면 등에서 "내가 올린 영상 목록"을 보여줄 때 사용하는 요약 DTO.
 *
 * - 썸네일: thumbnailUrl (없으면 프론트에서 기본 플레이 아이콘 사용)
 * - 제목: title
 * - 업로드 날짜: uploadDate
 * - 좋아요/싫어요/조회수: likeCount / dislikeCount / viewCount
 * - 태그: tag1 ~ tag5
 * - 인증/심사 상태: reviewStatus (예: 'P', 'A', 'H' 등) + isBlocked ('Y'/'N')
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoSummaryDto {

    // 기본 정보
    private Long videoNo;
    private String title;
    private String description;
    private String myReaction;
    // 날짜/집계 정보
    private LocalDateTime uploadDate;
    private Long viewCount;
    private Long likeCount;
    private Long dislikeCount;

    // 태그 정보
    private String tag1;
    private String tag2;
    private String tag3;
    private String tag4;
    private String tag5;

    // 심사/차단 상태
    private String reviewStatus;   // 'P' / 'A' / 'H' ...
    private String isBlocked;      // 'Y' or 'N'

    // 썸네일 URL (필요 시 서비스/컨트롤러에서 세팅)
    private String thumbnailUrl;

    /**
     * 엔티티 Video → 요약 DTO 변환 헬퍼.
     */
    public static VideoSummaryDto from(Video v) {
        if (v == null) {
            return null;
        }

        return VideoSummaryDto.builder()
                .videoNo(v.getVideoNo())
                .title(v.getTitle())
                .description(v.getDescription())
                .uploadDate(v.getUploadDate())
                .viewCount(v.getViewCount())
                .likeCount(v.getLikeCount())
                .dislikeCount(v.getDislikeCount())
                .tag1(v.getTag1())
                .tag2(v.getTag2())
                .tag3(v.getTag3())
                .tag4(v.getTag4())
                .tag5(v.getTag5())
                .reviewStatus(v.getReviewStatus())
                .isBlocked(v.getIsBlocked())
                .thumbnailUrl(null)
                .build();
    }
}
