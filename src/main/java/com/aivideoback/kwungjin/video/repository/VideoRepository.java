// src/main/java/com/aivideoback/kwungjin/video/repository/VideoRepository.java
package com.aivideoback.kwungjin.video.repository;

import com.aivideoback.kwungjin.video.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    // 나중에 태그/검색용 메서드 추가 가능
}
