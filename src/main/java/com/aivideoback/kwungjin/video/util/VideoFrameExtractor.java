package com.aivideoback.kwungjin.video.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class VideoFrameExtractor {

    private static String resolveFfmpegCommand() {
        String fromEnv = System.getenv("FFMPEG_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) {
            log.info("FFMPEG_PATH detected: {}", fromEnv);
            return fromEnv;   // 예: C:\ffmpeg\bin\ffmpeg.exe
        }
        // 기본값: PATH에 ffmpeg가 잡혀있다고 가정
        log.info("FFMPEG_PATH not set. Using default command: ffmpeg");
        return "ffmpeg";
    }

    public static List<File> extractThumbnailFrames(byte[] videoBytes) throws IOException, InterruptedException {

        String ffmpegCmd = resolveFfmpegCommand();

        // 1) 임시 비디오 파일로 저장
        File tempVideo = File.createTempFile("video-src-", ".mp4");
        try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
            fos.write(videoBytes);
        }

        // 2) 출력 프레임 파일 패턴
        File frameDir = new File(System.getProperty("java.io.tmpdir"), "video-frames");
        if (!frameDir.exists() && !frameDir.mkdirs()) {
            throw new IOException("Failed to create frame directory: " + frameDir.getAbsolutePath());
        }

        String outputPattern = new File(frameDir, "frame-%03d.jpg").getAbsolutePath();

        // 3) ffmpeg 명령 구성 (예: 1초에 1프레임)
        List<String> command = new ArrayList<>();
        command.add(ffmpegCmd);
        command.add("-y");                   // 덮어쓰기
        command.add("-i");
        command.add(tempVideo.getAbsolutePath());
        command.add("-vf");
        command.add("fps=1");
        command.add(outputPattern);

        log.info("Running ffmpeg command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // 에러 로그도 같이 보기 원하면
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("ffmpeg exited with code {}", exitCode);
            throw new IOException("ffmpeg failed with exit code " + exitCode);
        }

        // 4) 생성된 프레임 파일들 수집
        File[] files = frameDir.listFiles((dir, name) -> name.startsWith("frame-") && name.endsWith(".jpg"));
        List<File> frames = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                frames.add(f);
            }
        }

        log.info("Extracted {} frames via ffmpeg", frames.size());
        return frames;
    }
}
