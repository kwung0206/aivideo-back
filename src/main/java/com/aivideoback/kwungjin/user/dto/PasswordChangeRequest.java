// src/main/java/com/aivideoback/kwungjin/user/dto/PasswordChangeRequest.java
package com.aivideoback.kwungjin.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    private String newPassword;
}
