package com.aivideoback.kwungjin.video.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VideoAutoTagRequest {

    private Long videoNo;

    private TagScore mainTag;
    private List<TagScore> subTags;
    private List<TagScore> presentTags;

    // label 별 확률: { "game": 0.91, "space": 0.07, ... }
    private Map<String, Double> allScores;

    private Integer frameCount;

    @Data
    public static class TagScore {
        private String name;
        private Double score;
    }
}
