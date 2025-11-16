// src/main/java/com/aivideoback/kwungjin/admin/dto/BlockedVideoDto.java
package com.aivideoback.kwungjin.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BlockedVideoDto {

    private Long videoNo;
    private String title;
    private String uploaderNickname;
    private String uploaderId;
    private Long viewCount;
    private String createdAt;   // 업로드일 (uploadDate) 기반 ISO 문자열
}
