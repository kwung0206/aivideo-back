// src/main/java/com/aivideoback/kwungjin/video/dto/VideoReactionResponse.java
package com.aivideoback.kwungjin.video.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoReactionResponse {
    private Long likeCount;
    private Long dislikeCount;
    private String myReaction;   // "LIKE" / "DISLIKE" / null
}
