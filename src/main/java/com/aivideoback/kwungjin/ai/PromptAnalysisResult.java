// src/main/java/com/aivideoback/kwungjin/ai/PromptAnalysisResult.java
package com.aivideoback.kwungjin.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PromptAnalysisResult {

    /** ChatGPT가 요약한 검색 의도 */
    private final String intentSummary;

    /** 이 프롬프트로 찾으면 좋을 태그 키워드 리스트 */
    private final List<String> tags;
}
