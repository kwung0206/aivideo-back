// src/main/java/com/aivideoback/kwungjin/user/entity/EmailVerification.java
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
    @Column(name = "VERIFICATION_NO")
    private Long id;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "CODE_HASH", nullable = false, length = 200)
    private String codeHash;

    @Column(name = "ATTEMPTS", nullable = false)
    private Integer attempts;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;
}
