// src/main/java/com/aivideoback/kwungjin/video/repository/VideoFeatureRepository.java
package com.aivideoback.kwungjin.video.repository;

import com.aivideoback.kwungjin.video.entity.VideoFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoFeatureRepository extends JpaRepository<VideoFeature, Long> {

    List<VideoFeature> findByVideoNo(Long videoNo);

    void deleteByVideoNo(Long videoNo);

    void deleteByVideoNoAndSource(Long videoNo, String source);
    boolean existsByVideoNoAndSource(Long videoNo, String source);
}
