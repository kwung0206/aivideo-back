// src/main/java/com/aivideoback/kwungjin/finding/service/PromptFindingService.java
package com.aivideoback.kwungjin.finding.service;

import com.aivideoback.kwungjin.ai.PromptAnalysisResult;
import com.aivideoback.kwungjin.ai.PromptTagService;
import com.aivideoback.kwungjin.finding.dto.PromptFindingRequest;
import com.aivideoback.kwungjin.finding.dto.PromptFindingResponse;
import com.aivideoback.kwungjin.finding.dto.PromptFindingResponse.VideoMatchDto;
import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.entity.VideoFeature;
import com.aivideoback.kwungjin.video.repository.VideoFeatureRepository;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptFindingService {

    private final PromptTagService promptTagService;
    private final VideoRepository videoRepository;
    private final VideoFeatureRepository videoFeatureRepository;
    private final ObjectMapper objectMapper;   // Spring Boot가 자동으로 Bean 등록해줌

    @Transactional(readOnly = true)
    public PromptFindingResponse search(PromptFindingRequest request) {

        String prompt = request.getPrompt().trim();
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }

        // 1) ChatGPT로 프롬프트 분석 → 태그/요약
        PromptAnalysisResult analysis = promptTagService.analyzePrompt(prompt);
        List<String> tags = Optional.ofNullable(analysis.getTags()).orElse(List.of());
        Set<String> tagSetLower = tags.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // 2) 후보 영상 가져오기: 차단 X + 승인(A)인 최신 200개
        List<Video> candidates =
                videoRepository.findTop200ByIsBlockedAndReviewStatusOrderByCreatedAtDesc("N", "A");

        String promptLower = prompt.toLowerCase(Locale.ROOT);
        String sort = Optional.ofNullable(request.getSort()).orElse("latest");

        // 3) 각 영상별 matchScore 계산
        List<VideoMatchDto> matches = candidates.stream()
                .map(v -> mapToDtoWithScore(v, tagSetLower, promptLower))
                .filter(v -> v.getMatchScore() > 0.0)   // 완전 0점인 애들은 버림
                .sorted((a, b) -> {
                    int cmp;
                    switch (sort) {
                        case "views":
                            cmp = Long.compare(b.getViews(), a.getViews());
                            break;
                        case "likes":
                            cmp = Long.compare(b.getLikes(), a.getLikes());
                            break;
                        case "dislikes":
                            cmp = Long.compare(b.getDislikes(), a.getDislikes());
                            break;
                        case "oldest":
                            cmp = a.getCreatedAt().compareTo(b.getCreatedAt());
                            break;
                        case "latest":
                        default:
                            cmp = b.getCreatedAt().compareTo(a.getCreatedAt());
                            break;
                    }
                    if (cmp != 0) return cmp;
                    // 동일하면 matchScore 높은 순
                    return Double.compare(b.getMatchScore(), a.getMatchScore());
                })
                .toList();

        return PromptFindingResponse.builder()
                .originalPrompt(prompt)
                .intentSummary(analysis.getIntentSummary())
                .predictedTags(tags)
                .videos(matches)
                .build();
    }

    /**
     * 한 영상에 대해:
     *  - VideoFeature.tagsJson → 태그 로드 (가능하면)
     *  - 없으면 title/description 토큰화로 fallback
     *  - 그 태그들과 프롬프트 태그들의 겹치는 정도로 matchScore 계산
     */
    private VideoMatchDto mapToDtoWithScore(Video v,
                                            Set<String> queryTagsLower,
                                            String promptLower) {

        // 1) 영상 태그 로딩
        List<String> videoTags = resolveVideoTags(v);

        Set<String> videoTagsLower = videoTags.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // 2) 태그 겹치는 개수
        long overlap = videoTagsLower.stream()
                .filter(queryTagsLower::contains)
                .count();

        // 3) title/description 에 태그가 포함되는지
        String title = Optional.ofNullable(v.getTitle()).orElse("").toLowerCase(Locale.ROOT);
        String desc  = Optional.ofNullable(v.getDescription()).orElse("").toLowerCase(Locale.ROOT);

        long titleHits = queryTagsLower.stream().filter(title::contains).count();
        long descHits  = queryTagsLower.stream().filter(desc::contains).count();

        // 4) 프롬프트 전체 문장이 title/desc에 얼마나 포함되는지 (아주 단순)
        boolean titleContainsPrompt = title.contains(promptLower);
        boolean descContainsPrompt  = desc.contains(promptLower);

        double score = 0.0;

        // 태그 겹침에 높은 가중치
        score += overlap * 3.0;
        score += titleHits * 2.0;
        score += descHits * 1.0;
        if (titleContainsPrompt) score += 2.0;
        if (descContainsPrompt)  score += 1.0;

        // 정규화 (대충 최대값을 잡아서 0~1 사이로)
        double maxScore = Math.max(3.0 * Math.max(1, queryTagsLower.size()) + 5.0, 8.0);
        double normalized = Math.min(1.0, score / maxScore);

        String level;
        if (normalized >= 0.66) {
            level = "HIGH";
        } else if (normalized >= 0.33) {
            level = "MEDIUM";
        } else {
            level = "LOW";
        }

        return VideoMatchDto.builder()
                .videoNo(v.getVideoNo())
                .title(v.getTitle())
                .description(v.getDescription())
                .views(v.getViewCount())
                .likes(v.getLikeCount())
                .dislikes(v.getDislikeCount())
                .createdAt(v.getCreatedAt())
                .durationSec(0L)            // 🔹 아직 길이 컬럼 없으니 0L로
                .tags(videoTags)            // 🔹 여기서 빨간 줄 안 떠야 정상
                .matchScore(normalized)
                .matchLevel(level)
                .build();
    }

    /**
     * 1순위: VIDEO_FEATURE_TABLE.tagsJson 에서 태그 추출
     * 2순위: title + description 을 토큰화해서 태그처럼 사용
     */
    private List<String> resolveVideoTags(Video v) {
        // 1) VideoFeature.tagsJson 사용 시도
        try {
            List<VideoFeature> features = videoFeatureRepository.findByVideoNo(v.getVideoNo());
            Set<String> collected = new LinkedHashSet<>();

            for (VideoFeature feature : features) {
                String json = feature.getTagsJson();
                if (json == null || json.isBlank()) continue;

                Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
                Object tagsObj = parsed.get("tags");
                if (tagsObj instanceof Collection<?> col) {
                    for (Object o : col) {
                        if (o == null) continue;
                        String t = o.toString().trim();
                        if (!t.isEmpty()) collected.add(t);
                    }
                } else if (tagsObj instanceof String s) {
                    Arrays.stream(s.split("[,\n]"))
                            .map(String::trim)
                            .filter(str -> !str.isEmpty())
                            .forEach(collected::add);
                }
            }

            if (!collected.isEmpty()) {
                return List.copyOf(collected);
            }
        } catch (Exception e) {
            log.warn("VIDEO_FEATURE.tagsJson 파싱 중 오류 videoNo={}", v.getVideoNo(), e);
        }

        // 2) fallback: title + description 토큰화
        String text = (Optional.ofNullable(v.getTitle()).orElse("") + " " +
                Optional.ofNullable(v.getDescription()).orElse(""))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^가-힣a-z0-9\\s]", " ");

        return Arrays.stream(text.split("\\s+"))
                .filter(s -> s.length() >= 2)
                .limit(30)
                .toList();
    }
}
