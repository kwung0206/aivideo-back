// src/main/java/com/aivideoback/kwungjin/video/dto/DesktopTagTargetDto.java
package com.aivideoback.kwungjin.video.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesktopTagTargetDto {

    private Long videoNo;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime uploadDate;
}
