package com.aivideoback.kwungjin.user.dto;

import com.aivideoback.kwungjin.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private Long userNo;
    private String userId;
    private String nickname;
    private String gender;
    private Integer age;
    private String email;
    private String profileImage;
    private Long tokenCount;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userNo(user.getUserNo())
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .gender(user.getGender())
                .age(user.getAge())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .tokenCount(user.getTokenCount())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
