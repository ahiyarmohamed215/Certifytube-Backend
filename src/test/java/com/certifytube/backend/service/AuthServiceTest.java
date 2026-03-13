package com.certifytube.backend.service;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.exception.TokenValidationException;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.EmailVerificationToken;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.EmailVerificationTokenRepository;
import com.certifytube.backend.repository.PasswordResetTokenRepository;
import com.certifytube.backend.repository.RevokedTokenRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.certifytube.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private AuthRateLimitService authRateLimitService;
    @Mock
    private EmailDeliveryService emailDeliveryService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    void signUpShouldPersistTrimmedNameAndReturnIt() {
        SignUpRequest req = new SignUpRequest();
        req.setEmail("  User@Example.com ");
        req.setPassword("securePassword123");
        req.setName("  John Doe  ");

        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123")).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(11L);
            return user;
        });
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountMapper.toAuthResponse(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .name(user.getName())
                    .build();
        });

        AuthResponse response = authService.signUp(req);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        UserAccount savedUser = userCaptor.getValue();

        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("John Doe", savedUser.getName());
        assertEquals("John Doe", response.getName());
        assertNull(response.getToken());
        assertNull(response.getTokenType());
        assertEquals("Signup successful. Please verify your email to continue.", response.getMessage());
        verify(emailDeliveryService).sendEmailVerification(eq("user@example.com"), any(String.class));
    }

    @Test
    void loginShouldReturnNameFromDbUserRecord() {
        LoginRequest req = new LoginRequest();
        req.setEmail("  user@example.com ");
        req.setPassword("securePassword123");

        UserAccount user = UserAccount.builder()
                .id(10L)
                .email("user@example.com")
                .name("John Doe")
                .passwordHash("hashed-password")
                .role(Role.LEARNER)
                .emailVerified(true)
                .createdAtUtc(Instant.now())
                .build();

        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("securePassword123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken("user@example.com", "LEARNER")).thenReturn("jwt-login");
        when(userAccountMapper.toAuthResponse(user)).thenReturn(AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getName())
                .build());

        AuthResponse response = authService.login(req);

        assertEquals("John Doe", response.getName());
        assertEquals("jwt-login", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertNull(response.getMessage());
    }

    @Test
    void verifyEmailShouldBeIdempotentWhenTokenIsReused() {
        String rawToken = "sample-token";
        String tokenHash = sha256Hex(rawToken);
        Instant future = Instant.now().plusSeconds(3600);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(101L)
                .userId(44L)
                .tokenHash(tokenHash)
                .createdAtUtc(Instant.now())
                .expiresAtUtc(future)
                .usedAtUtc(null)
                .build();

        UserAccount user = UserAccount.builder()
                .id(44L)
                .email("learner@example.com")
                .name("Learner")
                .passwordHash("hash")
                .role(Role.LEARNER)
                .emailVerified(false)
                .createdAtUtc(Instant.now())
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userAccountRepository.findById(44L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.verifyEmail(rawToken);
        authService.verifyEmail(rawToken);

        assertTrue(Boolean.TRUE.equals(user.getEmailVerified()));
        verify(userAccountRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
        verify(emailVerificationTokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    void verifyEmailShouldRejectAlreadyUsedTokenWhenUserStillNotVerified() {
        String rawToken = "used-token";
        String tokenHash = sha256Hex(rawToken);
        Instant now = Instant.now();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(55L)
                .userId(88L)
                .tokenHash(tokenHash)
                .createdAtUtc(now.minusSeconds(10))
                .expiresAtUtc(now.plusSeconds(3600))
                .usedAtUtc(now.minusSeconds(5))
                .build();

        UserAccount user = UserAccount.builder()
                .id(88L)
                .email("still-unverified@example.com")
                .name("Learner")
                .passwordHash("hash")
                .role(Role.LEARNER)
                .emailVerified(false)
                .createdAtUtc(now.minusSeconds(100))
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userAccountRepository.findById(88L)).thenReturn(Optional.of(user));

        TokenValidationException ex = assertThrows(TokenValidationException.class, () -> authService.verifyEmail(rawToken));
        assertEquals("TOKEN_ALREADY_USED", ex.getCode());
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
            throw new RuntimeException(e);
        }
    }
}
