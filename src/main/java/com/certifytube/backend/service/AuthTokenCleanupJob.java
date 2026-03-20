package com.certifytube.backend.service;

import com.certifytube.backend.repository.EmailVerificationTokenRepository;
import com.certifytube.backend.repository.PasswordResetTokenRepository;
import com.certifytube.backend.repository.AuthRateLimitEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuthTokenCleanupJob {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AuthRateLimitEventRepository authRateLimitEventRepository;

    @Scheduled(fixedDelayString = "${auth.tokens.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteByExpiresAtUtcBefore(now);
        emailVerificationTokenRepository.deleteByExpiresAtUtcBefore(now);
        authRateLimitEventRepository.deleteByCreatedAtUtcBefore(now.minusSeconds(86400));
    }
}
