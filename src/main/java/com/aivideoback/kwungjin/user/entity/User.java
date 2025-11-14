package com.aivideoback.kwungjin.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "USER_TABLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @SequenceGenerator(
            name = "USER_SEQ_GENERATOR",
            sequenceName = "USER_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "USER_SEQ_GENERATOR")
    @Column(name = "USER_NO")
    private Long userNo;          // 유저 넘버 (PK)

    @Column(name = "USER_ID", nullable = false, length = 100, unique = true)
    private String userId;        // 아이디

    @Column(name = "USER_PASSWORD", nullable = false, length = 200)
    private String password;      // 비밀번호(해시)

    @Column(name = "USER_NICKNAME", nullable = false, length = 100, unique = true)
    private String nickname;      // 닉네임

    @Column(name = "USER_GENDER", length = 1)
    private String gender;        // 성별 (M/F 등)

    @Column(name = "USER_AGE")
    private Integer age;          // 나이

    @Column(name = "USER_EMAIL", nullable = false, length = 255, unique = true)
    private String email;         // 이메일

    @Column(name = "PROFILE_IMAGE", length = 4000)
    private String profileImage;  // 프로필 이미지 경로/URL

    @Column(name = "TOKEN_COUNT", nullable = false)
    private Long tokenCount;      // 토큰 수

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.tokenCount == null) {
            this.tokenCount = 5L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
