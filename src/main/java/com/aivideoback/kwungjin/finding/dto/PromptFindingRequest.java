// src/main/java/com/aivideoback/kwungjin/finding/dto/PromptFindingRequest.java
package com.aivideoback.kwungjin.finding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptFindingRequest {

    @NotBlank
    private String prompt;

    /**
     * 정렬 기준: views, latest, oldest, likes, dislikes
     */
    private String sort = "latest";
}
