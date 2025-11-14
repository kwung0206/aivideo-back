package com.aivideoback.kwungjin.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String password;
}
