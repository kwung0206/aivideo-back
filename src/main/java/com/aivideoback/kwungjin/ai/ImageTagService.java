package com.aivideoback.kwungjin.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageTagService {

    private final RestTemplate restTemplate;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    // Responses API 엔드포인트
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-4.1-mini";

    /**
     * 여러 프레임을 받아서 GPT에게 태그를 요청
     */
    public List<String> extractTagsFromFrames(List<byte[]> frames) {
        if (frames == null || frames.isEmpty()) {
            log.warn("이미지 프레임이 비어있음");
            return List.of();
        }

        // 프레임 너무 많으면 상위 몇 개만 사용 (예: 3장)
        List<byte[]> selectedFrames = frames.size() > 3 ? frames.subList(0, 3) : frames;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        // 1) content 배열 구성
        List<Map<String, Object>> content = new ArrayList<>();

        // 텍스트 프롬프트
        content.add(Map.of(
                "type", "input_text",
                "text", """
                        다음 이미지들에서 공통적인 주제를 잘 설명하는 한글 태그를 최대 10개까지만 뽑아줘.
                        형식은 "태그1, 태그2, 태그3" 처럼 콤마로 구분된 한 줄 텍스트로만 답변해.
                        설명 문장은 쓰지 마.
                        """
        ));

        // 이미지들(Base64 → data URL)
        for (byte[] frame : selectedFrames) {
            String b64 = Base64.getEncoder().encodeToString(frame);

            // ❗ 여기서 image_url 은 "문자열" 이어야 함 (이전처럼 {url: "..."} 객체 ❌)
            content.add(Map.of(
                    "type", "input_image",
                    "image_url", "data:image/jpeg;base64," + b64
            ));
        }

        // 2) 최종 payload (Responses API 형식)
        Map<String, Object> payload = Map.of(
                "model", MODEL,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", content
                )),
                "max_output_tokens", 256,
                "temperature", 0.2
                // ⚠ response_format 는 Responses API에서 text.format 으로 바뀌었으니 아예 안 쓰는 게 안전
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    OPENAI_RESPONSES_URL, entity, Map.class);

            if (resp == null) {
                log.warn("OpenAI 응답이 null");
                return List.of();
            }

            // Responses API 응답 구조:
            // output[0].content[0].text  에 실제 응답 텍스트가 들어 있음
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> output =
                    (List<Map<String, Object>>) resp.get("output");

            if (output == null || output.isEmpty()) {
                log.warn("OpenAI output이 비어있음: {}", resp);
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outContent =
                    (List<Map<String, Object>>) output.get(0).get("content");

            if (outContent == null || outContent.isEmpty()) {
                log.warn("OpenAI content가 비어있음: {}", resp);
                return List.of();
            }

            Object textObj = outContent.get(0).get("text");
            String text = String.valueOf(textObj);

            // "태그1, 태그2, 태그3" → List<String>
            List<String> tags = Arrays.stream(text.split("[,\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            log.info("이미지 태그 추출 결과: {}", tags);
            return tags;

        } catch (HttpClientErrorException e) {
            log.error("OpenAI API error status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("OpenAI API 호출 중 예외 발생", e);
            return List.of();
        }
    }
}
