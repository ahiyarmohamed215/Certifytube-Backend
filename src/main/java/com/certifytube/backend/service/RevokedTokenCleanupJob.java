package com.certifytube.backend.service;

import com.certifytube.backend.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RevokedTokenCleanupJob {

    private final RevokedTokenRepository revokedTokenRepository;

    @Scheduled(fixedDelayString = "${auth.jwt.revoked-cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredRevokedTokens() {
        revokedTokenRepository.deleteByExpiresAtUtcBefore(LocalDateTime.now());
    }
}
