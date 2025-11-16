// src/main/java/com/aivideoback/kwungjin/admin/repository/AdminRepository.java
package com.aivideoback.kwungjin.admin.repository;

import com.aivideoback.kwungjin.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByAdminId(String adminId);

    boolean existsByAdminId(String adminId);
}
