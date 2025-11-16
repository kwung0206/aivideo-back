// src/main/java/com/aivideoback/kwungjin/video/entity/VideoReaction.java
package com.aivideoback.kwungjin.video.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "VIDEO_REACTION_TABLE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_VIDEO_REACTION_VIDEO_USER",
                        columnNames = {"VIDEO_NO", "USER_NO"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoReaction {

    @Id
    @SequenceGenerator(
            name = "VIDEO_REACTION_SEQ_GENERATOR",
            sequenceName = "VIDEO_REACTION_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "VIDEO_REACTION_SEQ_GENERATOR")
    @Column(name = "REACTION_NO")
    private Long reactionNo;

    @Column(name = "VIDEO_NO", nullable = false)
    private Long videoNo;

    @Column(name = "USER_NO", nullable = false)
    private Long userNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "REACTION_TYPE", nullable = false, length = 10)
    private ReactionType reactionType;   // LIKE / DISLIKE

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReactionType {
        LIKE, DISLIKE
    }
}
