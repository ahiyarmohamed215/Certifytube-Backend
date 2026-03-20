package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_pwd_reset_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_pwd_reset_user", columnList = "user_id"),
        @Index(name = "idx_pwd_reset_expiry", columnList = "expires_at_utc")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "expires_at_utc", nullable = false)
    private LocalDateTime expiresAtUtc;

    @Column(name = "used_at_utc")
    private LocalDateTime usedAtUtc;

    @Column(name = "created_at_utc", nullable = false)
    private LocalDateTime createdAtUtc;
}
