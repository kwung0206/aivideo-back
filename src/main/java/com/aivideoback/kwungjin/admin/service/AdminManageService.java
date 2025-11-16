// src/main/java/com/aivideoback/kwungjin/admin/service/AdminManageService.java
package com.aivideoback.kwungjin.admin.service;

import com.aivideoback.kwungjin.admin.dto.AdminUserSummaryDto;
import com.aivideoback.kwungjin.admin.dto.BlockedVideoDto;
import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import com.aivideoback.kwungjin.video.entity.Video;
import com.aivideoback.kwungjin.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminManageService {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    // "2025-11-16T15:32:10" 이런 형태
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 전체 유저 목록 */
    public List<AdminUserSummaryDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    /** 차단된 영상 목록 */
    public List<BlockedVideoDto> getBlockedVideos() {
        // 1) isBlocked = 'Y' 인 동영상들 조회
        List<Video> blocked = videoRepository.findByIsBlocked("Y");

        if (blocked.isEmpty()) {
            return Collections.emptyList();
        }

        // 2) 해당 영상들의 userNo 모아서 업로더 유저들 한 번에 조회
        Set<Long> userNos = blocked.stream()
                .map(Video::getUserNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findAllById(userNos).stream()
                .collect(Collectors.toMap(User::getUserNo, Function.identity()));

        // 3) DTO로 변환
        return blocked.stream()
                .map(v -> {
                    User uploader = userMap.get(v.getUserNo());
                    return toBlockedDto(v, uploader);
                })
                .collect(Collectors.toList());
    }

    /** User → AdminUserSummaryDto 변환 */
    private AdminUserSummaryDto toUserDto(User u) {
        return AdminUserSummaryDto.builder()
                .userNo(u.getUserNo())
                .userId(u.getUserId())
                .nickname(u.getNickname())
                .email(u.getEmail())
                .tokenCount(u.getTokenCount())
                // User 엔티티에 status 필드가 없으니까 일단 ACTIVE 고정값
                .status("ACTIVE")
                .createdAt(u.getCreatedAt() != null
                        ? u.getCreatedAt().format(ISO_FMT)
                        : null)
                .build();
    }

    /** Video(+업로더) → BlockedVideoDto 변환 */
    private BlockedVideoDto toBlockedDto(Video v, User uploader) {
        return BlockedVideoDto.builder()
                .videoNo(v.getVideoNo())
                .title(v.getTitle())
                .uploaderNickname(uploader != null ? uploader.getNickname() : null)
                .uploaderId(uploader != null ? uploader.getUserId() : null)
                .viewCount(v.getViewCount())
                // 업로드일 기준으로 표기 (원하면 createdAt으로 바꿔도 됨)
                .createdAt(v.getUploadDate() != null
                        ? v.getUploadDate().format(ISO_FMT)
                        : null)
                .build();
    }
}
