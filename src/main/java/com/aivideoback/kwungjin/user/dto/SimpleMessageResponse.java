package com.aivideoback.kwungjin.user.dto;

public record SimpleMessageResponse(
        String message
) {
    public static SimpleMessageResponse of(String message) {
        return new SimpleMessageResponse(message);
    }
}
