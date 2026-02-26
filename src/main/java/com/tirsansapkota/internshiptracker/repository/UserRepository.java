package com.tirsansapkota.internshiptracker.repository;

import com.tirsansapkota.internshiptracker.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPendingEmailIgnoreCase(String email);

    // ✅ NOT static
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByPendingEmailIgnoreCaseAndIdNot(String email, Long id);
}