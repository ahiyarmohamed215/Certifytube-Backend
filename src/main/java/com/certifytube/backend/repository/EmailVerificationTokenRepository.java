package com.certifytube.backend.repository;

import com.certifytube.backend.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtUtcBefore(Instant cutoff);
}
