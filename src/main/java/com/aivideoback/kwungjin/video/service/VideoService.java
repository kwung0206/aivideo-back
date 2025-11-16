// src/main/java/com/aivideoback/kwungjin/video/service/VideoService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import com.aivideoback.kwungjin.video.dto.VideoReactionResponse;
import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.dto.VideoSummaryDto;
import com.aivideoback.kwungjin.video.dto.VideoUpdateRequest;
import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.entity.VideoReaction;
import com.aivideoback.kwungjin.video.entity.VideoReaction.ReactionType;
import com.aivideoback.kwungjin.video.repository.VideoFeatureRepository;
import com.aivideoback.kwungjin.video.repository.VideoReactionRepository;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.aivideoback.kwungjin.video.dto.HomeSummaryResponse;
import com.aivideoback.kwungjin.video.dto.HomeSummaryResponse.SimpleVideoDto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final VideoReactionRepository videoReactionRepository;
    // ✅ 자동 심사용 서비스
    private final VideoReviewService videoReviewService;
    private final VideoFeatureRepository videoFeatureRepository;

    public VideoResponse uploadVideo(
            String userId,
            String title,
            String description,
            List<String> tags,
            MultipartFile file
    ) throws IOException {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Long userNo = user.getUserNo();

        Video video = new Video();
        video.setUserNo(userNo);
        video.setTitle(title);
        video.setDescription(description);

        video.setFileName(file.getOriginalFilename());
        video.setContentType(file.getContentType());
        video.setFileSize(file.getSize());
        video.setFileData(file.getBytes());

        if (tags != null && !tags.isEmpty()) {
            if (tags.size() > 0) video.setTag1(tags.get(0));
            if (tags.size() > 1) video.setTag2(tags.get(1));
            if (tags.size() > 2) video.setTag3(tags.get(2));
            if (tags.size() > 3) video.setTag4(tags.get(3));
            if (tags.size() > 4) video.setTag5(tags.get(4));
        }

        LocalDateTime now = LocalDateTime.now();
        video.setUploadDate(now);
        video.setCreatedAt(now);
        video.setViewCount(0L);
        video.setLikeCount(0L);
        video.setDislikeCount(0L);
        video.setIsBlocked("N");
        video.setReviewStatus("P"); // 기본: 심사 대기

        Video saved = videoRepository.save(video);

        // ✅ 업로드 직후, Google Video Intelligence API로 비동기 심사 요청
        try {
            videoReviewService.reviewVideoAsync(saved.getVideoNo(), saved.getFileData());
        } catch (Exception e) {
            log.warn("영상 자동 심사 스케줄링 실패 videoNo={}", saved.getVideoNo(), e);
        }

        return VideoResponse.from(saved);
    }

    // 🔹 userId 기준으로 내 영상 목록
    @Transactional(readOnly = true)
    public List<VideoSummaryDto> getMyVideosByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        return videoRepository.findByUserNoOrderByUploadDateDesc(user.getUserNo())
                .stream()
                .map(VideoSummaryDto::from)
                .toList();
    }

    // 🔹 스트리밍용 영상 단건 조회
    @Transactional(readOnly = true)
    public VideoResponse getVideoForStream(Long videoNo) {
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));
        return VideoResponse.from(video);
    }

    // 🔹 내 영상 제목 수정 (태그는 현재 프론트에서 막아둔 상태)
    public VideoSummaryDto updateMyVideo(String userId, Long videoNo, VideoUpdateRequest request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        if (!video.getUserNo().equals(user.getUserNo())) {
            throw new AccessDeniedException("본인이 업로드한 영상만 수정할 수 있습니다.");
        }

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            video.setTitle(request.getTitle().trim());
        }

        Video saved = videoRepository.save(video);
        return VideoSummaryDto.from(saved);
    }

    // ✅ 공개 갤러리용: 승인(A) & 차단 안 된 영상만
    //   + (옵션) 키워드 + (옵션) 태그 필터
    //   + (옵션) 로그인한 사용자의 myReaction 정보까지 포함
    @Transactional(readOnly = true)
    public Page<VideoSummaryDto> getPublicVideos(
            String keyword,
            List<String> tags,
            int page,
            int size,
            String userId   // 🔹 로그인 유저 (없으면 null)
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "uploadDate")
        );

        String keywordParam = (keyword == null || keyword.isBlank())
                ? null
                : keyword.trim();

        List<String> tagList = (tags == null)
                ? Collections.emptyList()
                : tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();

        boolean tagsEmpty = tagList.isEmpty();

        Page<Video> result = videoRepository.searchPublicVideos(
                keywordParam,
                tagList,
                tagsEmpty,
                pageable
        );

        // 🔸 비로그인: 좋아요는 숫자만, myReaction 은 항상 null
        if (userId == null || userId.isBlank()) {
            return result.map(VideoSummaryDto::from);
        }

        // 🔸 로그인: 이 유저가 각 영상에 어떤 반응을 했는지 같이 내려주기
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Long userNo = user.getUserNo();

        List<Long> videoNos = result.stream()
                .map(Video::getVideoNo)
                .toList();

        if (videoNos.isEmpty()) {
            return result.map(VideoSummaryDto::from);
        }

        List<VideoReaction> reactions =
                videoReactionRepository.findByVideoNoInAndUserNo(videoNos, userNo);

        Map<Long, ReactionType> reactionMap = reactions.stream()
                .collect(Collectors.toMap(
                        VideoReaction::getVideoNo,
                        VideoReaction::getReactionType
                ));

        return result.map(v -> {
            VideoSummaryDto dto = VideoSummaryDto.from(v);
            ReactionType rt = reactionMap.get(v.getVideoNo());
            if (rt != null) {
                dto.setMyReaction(rt.name()); // "LIKE" / "DISLIKE"
            }
            return dto;
        });
    }

    // ✅ 좋아요/싫어요 토글
    @Transactional
    public VideoReactionResponse toggleReaction(String userId, Long videoNo, String action) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        ReactionType target;
        if ("LIKE".equalsIgnoreCase(action)) {
            target = ReactionType.LIKE;
        } else if ("DISLIKE".equalsIgnoreCase(action)) {
            target = ReactionType.DISLIKE;
        } else {
            throw new IllegalArgumentException("지원하지 않는 action 입니다: " + action);
        }

        Long userNo = user.getUserNo();

        // 현재 내 반응 조회
        VideoReaction current = videoReactionRepository
                .findByVideoNoAndUserNo(videoNo, userNo)
                .orElse(null);

        String myReactionStr;

        if (current != null && current.getReactionType() == target) {
            // 같은 버튼 한 번 더 누름 → 취소 (행 삭제)
            videoReactionRepository.delete(current);
            myReactionStr = null;
        } else {
            // 없거나, 반대 반응 → target 으로 세팅
            if (current == null) {
                current = VideoReaction.builder()
                        .videoNo(videoNo)
                        .userNo(userNo)
                        .build();
            }
            current.setReactionType(target);
            videoReactionRepository.save(current);
            myReactionStr = target.name();   // "LIKE" or "DISLIKE"
        }

        // 최신 좋아요/싫어요 카운트 계산
        long likeCount = videoReactionRepository.countByVideoNoAndReactionType(videoNo, ReactionType.LIKE);
        long dislikeCount = videoReactionRepository.countByVideoNoAndReactionType(videoNo, ReactionType.DISLIKE);

        // VIDEO_TABLE에도 반영 (목록 조회에서 사용)
        video.setLikeCount(likeCount);
        video.setDislikeCount(dislikeCount);

        return VideoReactionResponse.builder()
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .myReaction(myReactionStr)
                .build();
    }
    @Transactional
    public void deleteMyVideo(String userId, Long videoNo) {

        // 1) 로그인 유저 조회 (userId -> User / userNo)
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        // 2) 영상 조회
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상 정보를 찾을 수 없습니다."));

        // 3) 본인이 올린 영상인지 확인 (Video 안에 userNo 필드가 있다고 가정)
        if (!video.getUserNo().equals(user.getUserNo())) {
            throw new AccessDeniedException("본인이 업로드한 영상만 삭제할 수 있습니다.");
        }

        // 4) 연관 데이터(자식) 먼저 삭제
        //    FK_VIDEO_FEATURE_VIDEO 때문에 여기서 Feature 먼저 지워줘야 ORA-02292 안 남
        videoFeatureRepository.deleteByVideoNo(videoNo);   // VIDEO_FEATURE_TABLE

        //    좋아요/싫어요 반응도 함께 정리
        videoReactionRepository.deleteByVideoNo(videoNo);  // VIDEO_REACTION_TABLE

        // TODO: 만약 다른 테이블(예: 조회 로그, 코멘트 등)이 video_no FK를 갖고 있으면
        //       이 자리에서 같이 deleteByVideoNo(...) 호출해 주면 됨.

        // 5) 부모(영상) 삭제
        videoRepository.delete(video);
    }

    @Transactional(readOnly = true)
    public HomeSummaryResponse getHomeSummary() {
        long total = videoRepository.countByIsBlockedAndReviewStatus("N", "A");

        Video topLikedEntity = videoRepository
                .findFirstByIsBlockedAndReviewStatusOrderByLikeCountDesc("N", "A")
                .orElse(null);

        Video topViewedEntity = videoRepository
                .findFirstByIsBlockedAndReviewStatusOrderByViewCountDesc("N", "A")
                .orElse(null);

        Video topDislikedEntity = videoRepository
                .findFirstByIsBlockedAndReviewStatusOrderByDislikeCountDesc("N", "A")
                .orElse(null);

        return HomeSummaryResponse.builder()
                .totalCount(total)
                .topLiked(toSimpleDto(topLikedEntity))
                .topViewed(toSimpleDto(topViewedEntity))
                .topDisliked(toSimpleDto(topDislikedEntity))
                .build();
    }

    private SimpleVideoDto toSimpleDto(Video v) {
        if (v == null) return null;

        // TAG1 ~ TAG5 → List<String> 으로 변환
        List<String> tags = new ArrayList<>();
        if (v.getTag1() != null && !v.getTag1().isBlank()) tags.add(v.getTag1());
        if (v.getTag2() != null && !v.getTag2().isBlank()) tags.add(v.getTag2());
        if (v.getTag3() != null && !v.getTag3().isBlank()) tags.add(v.getTag3());
        if (v.getTag4() != null && !v.getTag4().isBlank()) tags.add(v.getTag4());
        if (v.getTag5() != null && !v.getTag5().isBlank()) tags.add(v.getTag5());

        return SimpleVideoDto.builder()
                .videoNo(v.getVideoNo())
                .title(v.getTitle())
                .description(v.getDescription())

                // 아직 엔티티에 썸네일/URL 컬럼이 없으니까 일단 null 로 내려보내고
                // 프론트에서 videoNo 기준으로 URL 조합해서 쓸 수 있게 할 거야
                .thumbnailUrl(null)
                .videoUrl(null)

                .likeCount(v.getLikeCount())
                .dislikeCount(v.getDislikeCount())
                .viewCount(v.getViewCount())

                // Video 에서 바로 닉네임을 알 수 없으니 일단 null
                // 나중에 UserRepository 붙여서 userNo → nickname 가져오면 됨
                .uploaderNickname(null)

                .createdAt(v.getCreatedAt())
                .tags(tags)
                .build();
    }
    public long increaseViewCount(Long videoNo) {
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        Long current = video.getViewCount();
        if (current == null) current = 0L;

        long updated = current + 1;
        video.setViewCount(updated);  // @Transactional + JPA 변경감지로 자동 flush

        return updated;
    }
}
