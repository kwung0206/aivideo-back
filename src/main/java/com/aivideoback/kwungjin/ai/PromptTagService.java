// src/main/java/com/aivideoback/kwungjin/ai/PromptTagService.java
package com.aivideoback.kwungjin.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTagService {

    private final RestTemplate restTemplate;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL_NAME = "gpt-4.1-mini"; // 또는 gpt-5.1-mini 등

    public PromptAnalysisResult analyzePrompt(String prompt) {

        String systemPrompt = """
                너는 '영상'을 찾기 위한 태그 추출기이다.
                사용자가 원하는 영상을 자연어로 설명하면, 아래 JSON 형식만 반환해라.

                {
                  "intentSummary": "사용자가 찾는 영상 내용을 한 문장으로 요약 (한국어)",
                  "tags": ["짧은 키워드1", "짧은 키워드2", ...] 
                }

                규칙:
                - tags는 최대 12개까지.
                - 각 태그는 1~3단어짜리 짧은 키워드로(예: "RAG", "PyTorch", "Transformer", "입문", "실습", "강의").
                - 따옴표, 줄바꿈 등으로 인해 JSON이 깨지지 않게 주의해라.
                - JSON 이외의 텍스트는 절대 출력하지 말 것.
                """;

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL_NAME);
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> response;
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);
            response = res.getBody();
        } catch (Exception e) {
            log.error("OpenAI 호출 오류", e);
            // 실패 시, 그냥 전체 프롬프트를 intent로 쓰고 태그는 비워둔다
            return PromptAnalysisResult.builder()
                    .intentSummary(prompt)
                    .tags(Collections.emptyList())
                    .build();
        }

        try {
            // choices[0].message.content 안에 JSON 문자열이 들어있다고 가정
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> choice0 = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice0.get("message");
            String content = Objects.toString(message.get("content"), "{}");

            // content 가 JSON 문자열이므로 Jackson 등으로 파싱
            // (ObjectMapper 하나 공용으로 쓰는 게 좋음)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> json = mapper.readValue(content, Map.class);

            String intentSummary = Objects.toString(json.getOrDefault("intentSummary", prompt));
            List<String> tags = new ArrayList<>();
            Object tagsObj = json.get("tags");
            if (tagsObj instanceof List<?> rawList) {
                for (Object o : rawList) {
                    if (o != null) {
                        String t = o.toString().trim();
                        if (!t.isEmpty()) tags.add(t);
                    }
                }
            }

            log.info("프롬프트 분석 결과 intent='{}', tags={}", intentSummary, tags);

            return PromptAnalysisResult.builder()
                    .intentSummary(intentSummary)
                    .tags(tags)
                    .build();

        } catch (Exception e) {
            log.error("프롬프트 분석 결과 파싱 오류", e);
            return PromptAnalysisResult.builder()
                    .intentSummary(prompt)
                    .tags(Collections.emptyList())
                    .build();
        }
    }
}
