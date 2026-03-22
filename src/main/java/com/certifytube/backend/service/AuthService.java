package com.certifytube.backend.service;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.ChangePasswordRequest;
import com.certifytube.backend.dto.ForgotPasswordRequest;
import com.certifytube.backend.dto.ForgotPasswordResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.ResendVerificationRequest;
import com.certifytube.backend.dto.ResetPasswordRequest;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.exception.TokenValidationException;
import com.certifytube.backend.model.EmailVerificationToken;
import com.certifytube.backend.model.PasswordResetToken;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.RevokedToken;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.EmailVerificationTokenRepository;
import com.certifytube.backend.repository.PasswordResetTokenRepository;
import com.certifytube.backend.repository.RevokedTokenRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.certifytube.backend.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AuthRateLimitService authRateLimitService;
    private final EmailDeliveryService emailDeliveryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAccountMapper userAccountMapper;

    @Value("${auth.password-reset.expiry-minutes:15}")
    private long passwordResetExpiryMinutes = 15;

    @Value("${auth.email-verification.expiry-hours:24}")
    private long emailVerificationExpiryHours = 24;

    @Value("${auth.signup.send-verification-email:true}")
    private boolean signupSendVerificationEmail = true;

    @Value("${auth.signup.fail-on-email-error:false}")
    private boolean signupFailOnEmailError = false;

    @Transactional
    public AuthResponse signUp(SignUpRequest req) {
        long flowStartedAt = System.nanoTime();
        String email = req.getEmail().trim().toLowerCase();
        String name = req.getName().trim();
        log.info("AUTH_SIGNUP_FLOW_START email={}", maskEmail(email));

        if (name.length() < 2 || name.length() > 255) {
            throw new IllegalArgumentException("Name must be between 2 and 255 characters");
        }

        long duplicateCheckStartedAt = System.nanoTime();
        UserAccount existing = userAccountRepository.findByEmail(email).orElse(null);
        log.info("AUTH_SIGNUP_DUPLICATE_CHECK email={} existing={} verified={} durationMs={}",
                maskEmail(email),
                existing != null,
                existing != null ? existing.getEmailVerified() : null,
                elapsedMs(duplicateCheckStartedAt));

        if (existing != null && Boolean.TRUE.equals(existing.getEmailVerified())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (existing != null && Boolean.FALSE.equals(existing.getActive())) {
            throw new IllegalStateException("Account is deactivated. Contact support.");
        }

        LocalDateTime now = LocalDateTime.now();
        long passwordEncodeStartedAt = System.nanoTime();
        String passwordHash = passwordEncoder.encode(req.getPassword());
        log.info("AUTH_SIGNUP_PASSWORD_ENCODED email={} durationMs={}", maskEmail(email), elapsedMs(passwordEncodeStartedAt));

        UserAccount user;
        long saveStartedAt = System.nanoTime();
        if (existing == null) {
            user = userAccountRepository.save(UserAccount.builder()
                    .email(email)
                    .name(name)
                    .passwordHash(passwordHash)
                    .role(Role.LEARNER)
                    .createdAtUtc(now)
                    .emailVerified(false)
                    .emailVerifiedAtUtc(null)
                    .active(true)
                    .build());
        } else {
            existing.setName(name);
            existing.setPasswordHash(passwordHash);
            existing.setEmailVerified(false);
            existing.setEmailVerifiedAtUtc(null);
            user = userAccountRepository.save(existing);
        }
        log.info("AUTH_SIGNUP_USER_SAVED email={} userId={} durationMs={}",
                maskEmail(email), user.getId(), elapsedMs(saveStartedAt));

        long tokenStartedAt = System.nanoTime();
        String verifyToken = createAndStoreEmailVerificationToken(user.getId(), now);
        log.info("AUTH_SIGNUP_VERIFY_TOKEN_CREATED email={} durationMs={}", maskEmail(email), elapsedMs(tokenStartedAt));

        if (signupSendVerificationEmail) {
            long emailDispatchStartedAt = System.nanoTime();
            try {
                emailDeliveryService.sendEmailVerification(user.getEmail(), verifyToken);
                log.info("AUTH_SIGNUP_EMAIL_DISPATCH_QUEUED email={} durationMs={}",
                        maskEmail(email), elapsedMs(emailDispatchStartedAt));
            } catch (Exception ex) {
                if (signupFailOnEmailError) {
                    throw ex;
                }
                log.warn("AUTH_SIGNUP_EMAIL_DISPATCH_FAILED_NON_BLOCKING email={} reason={}",
                        maskEmail(email), ex.getMessage());
            }
        } else {
            log.warn("AUTH_SIGNUP_EMAIL_DISPATCH_SKIPPED email={} reason=auth.signup.send-verification-email=false",
                    maskEmail(email));
        }

        AuthResponse response = userAccountMapper.toAuthResponse(user);
        response.setToken(null);
        response.setTokenType(null);
        response.setMessage("Signup successful. Please verify your email to continue.");
        log.info("AUTH_SIGNUP_FLOW_DONE email={} userId={} totalDurationMs={}",
                maskEmail(email), user.getId(), elapsedMs(flowStartedAt));
        return response;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        long flowStartedAt = System.nanoTime();
        String email = req.getEmail().trim().toLowerCase();
        log.info("AUTH_LOGIN_FLOW_START email={}", maskEmail(email));

        long findUserStartedAt = System.nanoTime();
        UserAccount user = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        log.info("AUTH_LOGIN_USER_FOUND email={} userId={} durationMs={}",
                maskEmail(email), user.getId(), elapsedMs(findUserStartedAt));

        long passwordCheckStartedAt = System.nanoTime();
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        log.info("AUTH_LOGIN_PASSWORD_VERIFIED email={} durationMs={}", maskEmail(email), elapsedMs(passwordCheckStartedAt));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalStateException("Account is deactivated. Contact support.");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Email not verified. Please verify your email first.");
        }

        long tokenStartedAt = System.nanoTime();
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        log.info("AUTH_LOGIN_TOKEN_GENERATED email={} durationMs={}", maskEmail(email), elapsedMs(tokenStartedAt));

        AuthResponse response = userAccountMapper.toAuthResponse(user);
        response.setToken(token);
        response.setTokenType("Bearer");
        log.info("AUTH_LOGIN_FLOW_DONE email={} userId={} totalDurationMs={}",
                maskEmail(email), user.getId(), elapsedMs(flowStartedAt));
        return response;
    }

    @Transactional
    public void logout(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank())
            return;
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;

        try {
            String jti = jwtService.extractJti(token);
            Instant exp = jwtService.extractExpiration(token);
            if (jti != null && exp != null && exp.isAfter(Instant.now()) && !revokedTokenRepository.existsByJti(jti)) {
                revokedTokenRepository.save(RevokedToken.builder()
                        .jti(jti)
                        .expiresAtUtc(LocalDateTime.ofInstant(exp, ZoneId.systemDefault()))
                        .revokedAtUtc(LocalDateTime.now())
                        .build());
            }
            revokedTokenRepository.deleteByExpiresAtUtcBefore(LocalDateTime.now());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid/expired token can be treated as already logged out.
        }
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest req, String clientIp) {
        String email = req.getEmail().trim().toLowerCase();
        authRateLimitService.enforceAndRecord("FORGOT_PASSWORD", email, clientIp);

        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteByExpiresAtUtcBefore(now);
        emailVerificationTokenRepository.deleteByExpiresAtUtcBefore(now);

        UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
        String message = "If the email exists, a recovery email has been sent.";
        if (user != null) {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                String rawToken = createAndStorePasswordResetToken(user.getId(), now);
                emailDeliveryService.sendPasswordReset(user.getEmail(), rawToken);
            } else {
                String verifyToken = createAndStoreEmailVerificationToken(user.getId(), now);
                emailDeliveryService.sendEmailVerification(user.getEmail(), verifyToken);
            }
        }

        return ForgotPasswordResponse.builder()
                .message(message)
                .build();
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest req, String clientIp) {
        String email = req.getEmail().trim().toLowerCase();
        authRateLimitService.enforceAndRecord("RESEND_VERIFICATION", email, clientIp);

        LocalDateTime now = LocalDateTime.now();
        emailVerificationTokenRepository.deleteByExpiresAtUtcBefore(now);

        UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        String verifyToken = createAndStoreEmailVerificationToken(user.getId(), now);
        emailDeliveryService.sendEmailVerification(user.getEmail(), verifyToken);
    }

    @Transactional
    public void verifyEmail(String tokenRaw) {
        if (tokenRaw == null || tokenRaw.isBlank()) {
            throw new TokenValidationException("TOKEN_MISSING", "Verification token is required");
        }
        LocalDateTime now = LocalDateTime.now();
        emailVerificationTokenRepository.deleteByExpiresAtUtcBefore(now);

        String tokenHash = sha256Hex(tokenRaw.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenValidationException(
                        "TOKEN_INVALID_OR_EXPIRED",
                        "Invalid or expired verification token"
                ));

        UserAccount user = userAccountRepository.findById(token.getUserId())
                .orElseThrow(() -> new TokenValidationException(
                        "TOKEN_INVALID_OR_EXPIRED",
                        "Invalid or expired verification token"
                ));

        if (token.getExpiresAtUtc().isBefore(now)) {
            emailVerificationTokenRepository.delete(token);
            throw new TokenValidationException("TOKEN_INVALID_OR_EXPIRED", "Invalid or expired verification token");
        }

        if (token.getUsedAtUtc() != null) {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            throw new TokenValidationException(
                    "TOKEN_ALREADY_USED",
                    "Verification token already used. Request a new verification email."
            );
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            token.setUsedAtUtc(now);
            emailVerificationTokenRepository.save(token);
            return;
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAtUtc(now);
        userAccountRepository.save(user);

        token.setUsedAtUtc(now);
        emailVerificationTokenRepository.save(token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteByExpiresAtUtcBefore(now);

        String tokenRaw = req.getToken().trim();
        String tokenHash = sha256Hex(tokenRaw);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (resetToken.getUsedAtUtc() != null || resetToken.getExpiresAtUtc().isBefore(now)) {
            passwordResetTokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        UserAccount user = userAccountRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userAccountRepository.save(user);

        resetToken.setUsedAtUtc(now);
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userAccountRepository.save(user);
        passwordResetTokenRepository.deleteByUserId(userId);
    }

    private String createAndStorePasswordResetToken(Long userId, LocalDateTime now) {
        passwordResetTokenRepository.deleteByUserId(userId);
        String rawToken = createSecureToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .createdAtUtc(now)
                .expiresAtUtc(now.plusMinutes(passwordResetExpiryMinutes))
                .usedAtUtc(null)
                .build();
        passwordResetTokenRepository.save(token);
        return rawToken;
    }

    private String createAndStoreEmailVerificationToken(Long userId, LocalDateTime now) {
        emailVerificationTokenRepository.deleteByUserId(userId);
        String rawToken = createSecureToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .createdAtUtc(now)
                .expiresAtUtc(now.plusHours(emailVerificationExpiryHours))
                .usedAtUtc(null)
                .build();
        emailVerificationTokenRepository.save(token);
        return rawToken;
    }

    private String createSecureToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash reset token", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        String trimmed = email.trim().toLowerCase();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }
}
