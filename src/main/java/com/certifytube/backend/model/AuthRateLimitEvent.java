package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "auth_rate_limit_events", indexes = {
        @Index(name = "idx_auth_rate_action_subject_time", columnList = "action,subject_hash,created_at_utc"),
        @Index(name = "idx_auth_rate_created_time", columnList = "created_at_utc")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType; // EMAIL / IP

    @Column(name = "subject_hash", nullable = false, length = 128)
    private String subjectHash;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
