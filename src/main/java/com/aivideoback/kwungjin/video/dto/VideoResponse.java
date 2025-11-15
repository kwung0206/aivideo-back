// src/main/java/com/aivideoback/kwungjin/video/dto/VideoResponse.java
package com.aivideoback.kwungjin.video.dto;

import com.aivideoback.kwungjin.video.entity.Video;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoResponse {

    private Long videoNo;
    private Long userNo;
    private String title;
    private String description;
    private String fileName;
    private String contentType;
    private Long fileSize;

    // 스트리밍용 실제 데이터
    private byte[] fileData;

    private String tag1;
    private String tag2;
    private String tag3;
    private String tag4;
    private String tag5;

    private Long viewCount;
    private Long likeCount;
    private Long dislikeCount;

    private LocalDateTime uploadDate;
    private boolean blocked;

    public static VideoResponse from(Video v) {
        if (v == null) return null;
        return VideoResponse.builder()
                .videoNo(v.getVideoNo())
                .userNo(v.getUserNo())
                .title(v.getTitle())
                .description(v.getDescription())
                .fileName(v.getFileName())
                .contentType(v.getContentType())
                .fileSize(v.getFileSize())
                .fileData(v.getFileData())
                .tag1(v.getTag1())
                .tag2(v.getTag2())
                .tag3(v.getTag3())
                .tag4(v.getTag4())
                .tag5(v.getTag5())
                .viewCount(v.getViewCount())
                .likeCount(v.getLikeCount())
                .dislikeCount(v.getDislikeCount())
                .uploadDate(v.getUploadDate())
                .blocked("Y".equalsIgnoreCase(v.getIsBlocked()))
                .build();
    }
}
