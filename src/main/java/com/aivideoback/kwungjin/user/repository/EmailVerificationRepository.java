// src/main/java/com/aivideoback/kwungjin/user/repository/EmailVerificationRepository.java
package com.aivideoback.kwungjin.user.repository;

import com.aivideoback.kwungjin.user.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
