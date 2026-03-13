package com.certifytube.backend.repository;

import com.certifytube.backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtUtcBefore(Instant cutoff);
}
