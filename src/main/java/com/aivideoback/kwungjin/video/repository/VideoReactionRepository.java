// src/main/java/com/aivideoback/kwungjin/video/repository/VideoReactionRepository.java
package com.aivideoback.kwungjin.video.repository;

import com.aivideoback.kwungjin.video.entity.VideoReaction;
import com.aivideoback.kwungjin.video.entity.VideoReaction.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoReactionRepository extends JpaRepository<VideoReaction, Long> {

    Optional<VideoReaction> findByVideoNoAndUserNo(Long videoNo, Long userNo);

    long countByVideoNoAndReactionType(Long videoNo, ReactionType reactionType);

    void deleteByVideoNo(Long videoNo);
    List<VideoReaction> findByVideoNoInAndUserNo(List<Long> videoNos, Long userNo);
}
