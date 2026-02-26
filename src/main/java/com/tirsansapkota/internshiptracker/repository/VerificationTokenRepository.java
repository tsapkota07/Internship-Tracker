package com.tirsansapkota.internshiptracker.repository;
import org.springframework.data.jpa.repository.Modifying;

import com.tirsansapkota.internshiptracker.model.AppUser;
import com.tirsansapkota.internshiptracker.model.TokenType;
import com.tirsansapkota.internshiptracker.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByTokenAndType(String token, TokenType type);
    Optional<VerificationToken> findByToken(String token);

    @Query("""
        select t from VerificationToken t
        where t.user = :user
          and t.type = :type
          and t.used = false
          and t.expiresAt > :now
    """)
    List<VerificationToken> findActiveByUserAndType(
            @Param("user") AppUser user,
            @Param("type") TokenType type,
            @Param("now") LocalDateTime now
    );


//
//    @Modifying
//    @Query("delete from VerificationToken t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}