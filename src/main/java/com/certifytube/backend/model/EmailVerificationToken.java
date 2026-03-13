package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_email_verify_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_email_verify_user", columnList = "user_id"),
        @Index(name = "idx_email_verify_expiry", columnList = "expires_at_utc")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "expires_at_utc", nullable = false)
    private Instant expiresAtUtc;

    @Column(name = "used_at_utc")
    private Instant usedAtUtc;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
