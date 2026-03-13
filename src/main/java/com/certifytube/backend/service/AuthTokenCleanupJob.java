package com.certifytube.backend.service;

import com.certifytube.backend.repository.EmailVerificationTokenRepository;
import com.certifytube.backend.repository.PasswordResetTokenRepository;
import com.certifytube.backend.repository.AuthRateLimitEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AuthTokenCleanupJob {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AuthRateLimitEventRepository authRateLimitEventRepository;

    @Scheduled(fixedDelayString = "${auth.tokens.cleanup-ms:3600000}")
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        passwordResetTokenRepository.deleteByExpiresAtUtcBefore(now);
        emailVerificationTokenRepository.deleteByExpiresAtUtcBefore(now);
        authRateLimitEventRepository.deleteByCreatedAtUtcBefore(now.minusSeconds(86400));
    }
}
