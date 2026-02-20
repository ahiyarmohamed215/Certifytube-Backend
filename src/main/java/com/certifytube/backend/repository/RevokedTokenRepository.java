package com.certifytube.backend.repository;

import com.certifytube.backend.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    boolean existsByJti(String jti);
    void deleteByExpiresAtUtcBefore(Instant cutoff);
}
