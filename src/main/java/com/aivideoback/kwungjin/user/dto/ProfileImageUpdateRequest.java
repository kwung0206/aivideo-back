package com.aivideoback.kwungjin.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileImageUpdateRequest {

    @NotBlank(message = "프로필 이미지를 선택해 주세요.")
    private String profileImage;   // blue / purple / orange / green / pink / mono 같은 키
}
