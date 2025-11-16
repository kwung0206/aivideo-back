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
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final VideoReactionRepository videoReactionRepository;
    private final VideoReviewService videoReviewService;
    private final VideoFeatureRepository videoFeatureRepository;

    // 🔥 영상 파일이 저장될 기본 디렉터리 (컨테이너 기준 경로)
    @Value("${app.video.storage-dir:/data/videos}")
    private String videoStorageDir;

    @Transactional
    public VideoResponse uploadVideo(
            String userId,
            String title,
            String description,
            List<String> tags,
            MultipartFile file
    ) throws IOException {

        // 1) 사용자 조회
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Long userNo = user.getUserNo();

        // 2) 파일 이름/경로 먼저 준비
        String originalName = file.getOriginalFilename();

        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));  // ".mp4" 같은 확장자
        }

        // 저장용 파일명 (UUID 사용)
        String storedName = UUID.randomUUID().toString() + ext;

        // 저장 디렉터리: {storageDir}/{userNo}/
        Path userDir = Paths.get(videoStorageDir, String.valueOf(userNo));
        Files.createDirectories(userDir);

        // 실제 파일 경로
        Path targetPath = userDir.resolve(storedName);

        // 3) MultipartFile → 물리 파일로 먼저 복사
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 4) 이제 Video 엔티티를 "완전히" 채워서 한 번만 save
        LocalDateTime now = LocalDateTime.now();

        Video video = new Video();
        video.setUserNo(userNo);
        video.setTitle(title);
        video.setDescription(description);

        // ❗ FILE_NAME 에 무엇을 넣을지는 선택
        // - 실제 저장된 파일명: storedName
        // - 사용자가 업로드한 원본 이름: originalName
        // 여기서는 storedName을 넣었지만, 원한다면 originalName으로 바꿔도 됨
        video.setFileName(storedName);

        video.setContentType(file.getContentType());
        video.setFileSize(file.getSize());

        // ✅ FILE_PATH: NOT NULL 이므로 반드시 여기서 세팅
        video.setFilePath(targetPath.toString());

        // 태그
        if (tags != null && !tags.isEmpty()) {
            if (tags.size() > 0) video.setTag1(tags.get(0));
            if (tags.size() > 1) video.setTag2(tags.get(1));
            if (tags.size() > 2) video.setTag3(tags.get(2));
            if (tags.size() > 3) video.setTag4(tags.get(3));
            if (tags.size() > 4) video.setTag5(tags.get(4));
        }

        video.setUploadDate(now);
        video.setCreatedAt(now);
        video.setViewCount(0L);
        video.setLikeCount(0L);
        video.setDislikeCount(0L);
        video.setIsBlocked("N");
        video.setReviewStatus("P"); // 심사 대기

        // 5) INSERT 한 번만
        Video saved = videoRepository.save(video);

// 6) 업로드 직후, 비동기 심사 스케줄링 (videoNo만 넘김)
//    👉 트랜잭션 커밋이 끝난 다음에 돌도록 등록
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        videoReviewService.reviewVideoAsync(saved.getVideoNo());
                    } catch (Exception e) {
                        log.warn("영상 자동 심사 스케줄링 실패 videoNo={}", saved.getVideoNo(), e);
                    }
                }
            });
        } else {
            // 혹시 트랜잭션 밖에서 호출된 경우를 대비한 Fallback
            try {
                videoReviewService.reviewVideoAsync(saved.getVideoNo());
            } catch (Exception e) {
                log.warn("영상 자동 심사 스케줄링 실패 videoNo={}", saved.getVideoNo(), e);
            }
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

    // 🔹 스트리밍용 영상 단건 조회 (메타데이터만)
    @Transactional(readOnly = true)
    public VideoResponse getVideoForStream(Long videoNo) {
        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));
        return VideoResponse.from(video);
    }

    // 🔹 내 영상 제목 수정
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
        if (request.getDescription() != null) {
            video.setDescription(request.getDescription());
        }

        Video saved = videoRepository.save(video);
        return VideoSummaryDto.from(saved);
    }

    // ✅ 공개 갤러리용
    @Transactional(readOnly = true)
    public Page<VideoSummaryDto> getPublicVideos(
            String keyword,
            List<String> tags,
            int page,
            int size,
            String userId
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

        // 비로그인
        if (userId == null || userId.isBlank()) {
            return result.map(VideoSummaryDto::from);
        }

        // 로그인: myReaction 포함
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
            // 같은 버튼 한 번 더 → 취소
            videoReactionRepository.delete(current);
            myReactionStr = null;
        } else {
            // 없거나 반대 반응 → target 으로 세팅
            if (current == null) {
                current = VideoReaction.builder()
                        .videoNo(videoNo)
                        .userNo(userNo)
                        .build();
            }
            current.setReactionType(target);
            videoReactionRepository.save(current);
            myReactionStr = target.name();
        }

        long likeCount = videoReactionRepository.countByVideoNoAndReactionType(videoNo, ReactionType.LIKE);
        long dislikeCount = videoReactionRepository.countByVideoNoAndReactionType(videoNo, ReactionType.DISLIKE);

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

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상 정보를 찾을 수 없습니다."));

        if (!video.getUserNo().equals(user.getUserNo())) {
            throw new AccessDeniedException("본인이 업로드한 영상만 삭제할 수 있습니다.");
        }

        // 🔥 실제 영상 파일 삭제
        String path = video.getFilePath();
        if (path != null && !path.isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (IOException e) {
                log.warn("영상 파일 삭제 실패 path={} videoNo={}", path, videoNo, e);
            }
        }

        // 연관 데이터 삭제
        videoFeatureRepository.deleteByVideoNo(videoNo);
        videoReactionRepository.deleteByVideoNo(videoNo);

        // 부모 삭제
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
                .thumbnailUrl(null)
                .videoUrl(null)
                .likeCount(v.getLikeCount())
                .dislikeCount(v.getDislikeCount())
                .viewCount(v.getViewCount())
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
        video.setViewCount(updated);

        return updated;
    }
}
