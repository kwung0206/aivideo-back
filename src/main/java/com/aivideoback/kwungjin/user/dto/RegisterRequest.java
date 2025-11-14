package com.aivideoback.kwungjin.user.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    @Size(min = 4, max = 20)
    private String userId;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(min = 2, max = 20)
    private String nickname;

    @Pattern(regexp = "^[MF]$", message = "성별은 M 또는 F만 허용됩니다.")
    private String gender;

    @Min(0)
    @Max(150)
    private Integer age;

    @NotBlank
    @Email
    private String email;

    // 프로필 이미지 URL (선택)
    private String profileImage;
}
