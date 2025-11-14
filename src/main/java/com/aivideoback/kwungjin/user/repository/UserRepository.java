// src/main/java/com/aivideoback/kwungjin/user/repository/UserRepository.java
package com.aivideoback.kwungjin.user.repository;

import com.aivideoback.kwungjin.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUserId(String userId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    // ✅ JWT username(=USER_ID) 로 유저 조회
    Optional<User> findByUserId(String userId);
}
