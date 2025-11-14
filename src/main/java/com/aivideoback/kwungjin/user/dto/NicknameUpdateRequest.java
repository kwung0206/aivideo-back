// src/main/java/com/aivideoback/kwungjin/user/dto/NicknameUpdateRequest.java
package com.aivideoback.kwungjin.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NicknameUpdateRequest {

    @NotBlank(message = "변경할 닉네임을 입력해 주세요.")
    private String nickname;
}
