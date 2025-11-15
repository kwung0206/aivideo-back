// src/main/java/com/aivideoback/kwungjin/video/repository/VideoRepository.java
package com.aivideoback.kwungjin.video.repository;

import com.aivideoback.kwungjin.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    // 특정 유저가 올린 영상들을 업로드 날짜 기준 내림차순으로
    List<Video> findByUserNoOrderByUploadDateDesc(Long userNo);

    // ✅ 공개용: 차단 X + 승인(A) + (옵션) 키워드 + (옵션) 태그 필터
    @Query("""
        select v
        from Video v
        where v.isBlocked = 'N'
          and v.reviewStatus = 'A'
          and (:keyword is null or lower(v.title) like lower(concat('%', :keyword, '%')))
          and (
               :tagsEmpty = true
               or v.tag1 in :tags
               or v.tag2 in :tags
               or v.tag3 in :tags
               or v.tag4 in :tags
               or v.tag5 in :tags
          )
        order by v.uploadDate desc
        """)
    Page<Video> searchPublicVideos(
            @Param("keyword") String keyword,
            @Param("tags") List<String> tags,
            @Param("tagsEmpty") boolean tagsEmpty,
            Pageable pageable
    );
}
