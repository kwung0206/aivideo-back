package com.aivideoback.kwungjin.user.dto;

public record DuplicateCheckResponse(
        boolean available,
        String message
) {
    public static DuplicateCheckResponse of(boolean available, String label) {
        String msg = available
                ? "사용 가능한 " + label + "입니다."
                : "이미 사용 중인 " + label + "입니다.";
        return new DuplicateCheckResponse(available, msg);
    }
}
