// src/main/java/com/aivideoback/kwungjin/video/entity/VideoFeature.java
package com.aivideoback.kwungjin.video.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "VIDEO_FEATURE_TABLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoFeature {

    @Id
    @SequenceGenerator(
            name = "VIDEO_FEATURE_SEQ_GENERATOR",
            sequenceName = "VIDEO_FEATURE_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "VIDEO_FEATURE_SEQ_GENERATOR")
    @Column(name = "FEATURE_NO")
    private Long featureNo;

    @Column(name = "VIDEO_NO", nullable = false)
    private Long videoNo;

    // 이번 1차 구현에서는 프레임별이 아니라 "영상 전체 태그"로 쓸 거라 null로 두어도 됨
    @Column(name = "FRAME_TIME")
    private Double frameTime;

    @Column(name = "SOURCE", nullable = false, length = 30)
    private String source; // 예: "GPT_IMAGE"

    @Lob
    @Column(name = "TAGS_JSON", nullable = false)
    private String tagsJson; // {"tags":["학교","수업",...]} 이런 형식

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (source == null) {
            source = "GPT_IMAGE";
        }
    }
}
