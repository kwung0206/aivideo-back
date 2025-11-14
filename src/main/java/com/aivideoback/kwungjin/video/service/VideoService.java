// src/main/java/com/aivideoback/kwungjin/video/service/VideoService.java
package com.aivideoback.kwungjin.video.service;

import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import com.aivideoback.kwungjin.video.dto.VideoResponse;
import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;

    public VideoResponse uploadVideo(
            String userId,          // 🔁 userNo 대신 userId 받기
            String title,
            String description,
            List<String> tags,
            MultipartFile file
    ) throws IOException {

        // 1) 로그인 아이디로 유저 조회
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        Long userNo = user.getUserNo();

        // 2) Video 엔티티 생성
        Video video = new Video();
        video.setUserNo(userNo);
        video.setTitle(title);
        video.setDescription(description);

        video.setFileName(file.getOriginalFilename());
        video.setContentType(file.getContentType());
        video.setFileSize(file.getSize());
        video.setFileData(file.getBytes());

        // 태그 5개까지 매핑
        if (tags != null && !tags.isEmpty()) {
            if (tags.size() > 0) video.setTag1(tags.get(0));
            if (tags.size() > 1) video.setTag2(tags.get(1));
            if (tags.size() > 2) video.setTag3(tags.get(2));
            if (tags.size() > 3) video.setTag4(tags.get(3));
            if (tags.size() > 4) video.setTag5(tags.get(4));
        }

        // 기본값들
        LocalDateTime now = LocalDateTime.now();
        video.setUploadDate(now);
        video.setCreatedAt(now);
        video.setViewCount(0L);
        video.setLikeCount(0L);
        video.setDislikeCount(0L);
        video.setIsBlocked("N"); // 처음엔 정상

        Video saved = videoRepository.save(video);
        return VideoResponse.from(saved);
    }
}
