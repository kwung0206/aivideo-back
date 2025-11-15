// src/main/java/com/aivideoback/kwungjin/video/service/VideoService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.dto.VideoSummaryDto;
import com.aivideoback.kwungjin.video.dto.VideoUpdateRequest;
import com.aivideoback.kwungjin.video.entity.Video;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;

    // ✅ 새로 추가: 자동 심사용 서비스
    private final VideoReviewService videoReviewService;

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

    // 🔹 내 영상 삭제
    public void deleteMyVideo(String userId, Long videoNo) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Video video = videoRepository.findById(videoNo)
                .orElseThrow(() -> new IllegalArgumentException("영상이 존재하지 않습니다: " + videoNo));

        if (!video.getUserNo().equals(user.getUserNo())) {
            throw new AccessDeniedException("본인이 업로드한 영상만 삭제할 수 있습니다.");
        }

        videoRepository.delete(video);
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
    @Transactional(readOnly = true)
    public Page<VideoSummaryDto> getPublicVideos(String keyword, List<String> tags, int page, int size) {
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

        return result.map(VideoSummaryDto::from);
    }
}
