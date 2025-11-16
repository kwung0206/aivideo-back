// src/main/java/com/aivideoback/kwungjin/admin/dto/AdminUserSummaryDto.java
package com.aivideoback.kwungjin.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserSummaryDto {

    private Long userNo;
    private String userId;
    private String nickname;
    private String email;
    private Long tokenCount;
    private String status;      // 일단 "ACTIVE" 같은 문자열로 내려줄 용도
    private String createdAt;   // "yyyy-MM-dd'T'HH:mm:ss" 형태 문자열
}
