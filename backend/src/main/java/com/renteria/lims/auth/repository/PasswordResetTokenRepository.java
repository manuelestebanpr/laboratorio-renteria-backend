package com.renteria.lims.auth.repository;

import com.renteria.lims.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.userId = :userId AND t.used = false AND t.expiresAt > CURRENT_TIMESTAMP")
    long countActiveByUserId(@Param("userId") UUID userId);
}
