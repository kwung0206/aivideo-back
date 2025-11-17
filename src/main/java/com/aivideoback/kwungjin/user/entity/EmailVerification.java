package com.aivideoback.kwungjin.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "EMAIL_VERIFICATION_TABLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerification {

    @Id
    @SequenceGenerator(
            name = "EMAIL_VERIFICATION_SEQ_GENERATOR",
            sequenceName = "EMAIL_VERIFICATION_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "EMAIL_VERIFICATION_SEQ_GENERATOR")
    @Column(name = "EV_ID")
    private Long id;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "VERIFIED", nullable = false, length = 1)
    private String verified; // 'Y' or 'N'

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.verified == null) {
            this.verified = "N";
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isVerifiedFlag() {
        return "Y".equalsIgnoreCase(verified);
    }
}
