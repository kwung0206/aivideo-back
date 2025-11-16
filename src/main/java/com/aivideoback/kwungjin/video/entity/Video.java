// src/main/java/com/aivideoback/kwungjin/video/entity/Video.java
package com.aivideoback.kwungjin.video.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "VIDEO_TABLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @SequenceGenerator(
            name = "VIDEO_SEQ_GENERATOR",
            sequenceName = "VIDEO_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "VIDEO_SEQ_GENERATOR")
    @Column(name = "VIDEO_NO")
    private Long videoNo;                  // 영상 PK

    @Column(name = "USER_NO", nullable = false)
    private Long userNo;                   // 업로드한 유저 (FK → USER_TABLE.USER_NO)

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;                  // 영상 제목

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;            // 영상 설명

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;               // 원본 파일명

    @Column(name = "CONTENT_TYPE", nullable = false, length = 255)
    private String contentType;            // MIME 타입 (video/mp4 등)

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;                 // 파일 크기 (바이트)

    // 🔥 실제 서버 파일 경로 (예: /data/videos/{userNo}/{videoNo}.mp4)
    @Column(name = "FILE_PATH", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "TAG1", length = 100)
    private String tag1;

    @Column(name = "TAG2", length = 100)
    private String tag2;

    @Column(name = "TAG3", length = 100)
    private String tag3;

    @Column(name = "TAG4", length = 100)
    private String tag4;

    @Column(name = "TAG5", length = 100)
    private String tag5;

    @Column(name = "VIEW_COUNT", nullable = false)
    private Long viewCount;                // 조회수

    @Column(name = "LIKE_COUNT", nullable = false)
    private Long likeCount;                // 좋아요 수

    @Column(name = "DISLIKE_COUNT", nullable = false)
    private Long dislikeCount;             // 싫어요 수

    @Column(name = "UPLOAD_DATE", nullable = false)
    private LocalDateTime uploadDate;      // 업로드 날짜

    @Column(name = "IS_BLOCKED", nullable = false, length = 1)
    private String isBlocked;              // 'Y' = 차단, 'N' = 정상

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "REVIEW_STATUS", nullable = false, length = 1)
    private String reviewStatus;           // 'P' = 심사 대기, 'A' = 승인, 'H' = 보류 등

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)   createdAt = now;
        if (uploadDate == null)  uploadDate = now;
        if (viewCount == null)   viewCount = 0L;
        if (likeCount == null)   likeCount = 0L;
        if (dislikeCount == null) dislikeCount = 0L;
        if (isBlocked == null)   isBlocked = "N";
        if (reviewStatus == null) reviewStatus = "P";  // 기본: 심사 대기
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
