// src/main/java/com/aivideoback/kwungjin/user/dto/SimpleMessageResponse.java
package com.aivideoback.kwungjin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SimpleMessageResponse {

    private final String message;

    public static SimpleMessageResponse of(String message) {
        return new SimpleMessageResponse(message);
    }
}
