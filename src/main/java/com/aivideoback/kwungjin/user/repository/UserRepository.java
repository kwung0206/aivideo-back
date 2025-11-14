// src/main/java/com/aivideoback/kwungjin/user/repository/UserRepository.java
package com.aivideoback.kwungjin.user.repository;

import com.aivideoback.kwungjin.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ✅ ORA-00933 방지: fetch first 안 쓰고 count로만 검사
    @Query("select (count(u) > 0) from User u where u.userId = :userId")
    boolean existsByUserId(@Param("userId") String userId);

    @Query("select (count(u) > 0) from User u where u.nickname = :nickname")
    boolean existsByNickname(@Param("nickname") String nickname);

    @Query("select (count(u) > 0) from User u where u.email = :email")
    boolean existsByEmail(@Param("email") String email);

    // 로그인 시에 쓸 예정
    Optional<User> findByUserId(String userId);
}
