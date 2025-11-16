// src/main/java/com/aivideoback/kwungjin/admin/entity/Admin.java
package com.aivideoback.kwungjin.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADMIN_TABLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @SequenceGenerator(
            name = "ADMIN_SEQ_GENERATOR",
            sequenceName = "ADMIN_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ADMIN_SEQ_GENERATOR")
    @Column(name = "ADMIN_NO")
    private Long adminNo;

    @Column(name = "ADMIN_ID", nullable = false, unique = true)
    private String adminId;          // 로그인 아이디

    @Column(name = "ADMIN_PASSWORD", nullable = false)
    private String adminPassword;    // BCrypt 해시

    @Column(name = "ADMIN_NAME", nullable = false)
    private String adminName;

    @Column(name = "ADMIN_EMAIL", unique = true)
    private String adminEmail;

    @Column(name = "ADMIN_ROLE", nullable = false)
    private String adminRole;        // 예: "ADMIN" 또는 "ROLE_ADMIN"

    @Column(name = "ADMIN_STATUS", nullable = false)
    private String adminStatus;      // 예: "ACTIVE"

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.adminStatus == null) {
            this.adminStatus = "ACTIVE";
        }
        if (this.adminRole == null) {
            this.adminRole = "ADMIN";
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
