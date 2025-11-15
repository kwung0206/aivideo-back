package com.aivideoback.kwungjin.video.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class VideoUpdateRequest {
    private String title;
    private String description; // 필요하면 같이 수정
    private List<String> tags;  // 최대 5개 사용
}
