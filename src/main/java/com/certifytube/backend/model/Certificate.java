package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "certificates", indexes = {
        @Index(name = "idx_certificates_user_session", columnList = "user_id,session_id"),
        @Index(name = "idx_certificates_verify_token", columnList = "verification_token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @Column(name = "certificate_id", length = 36)
    private String certificateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "quiz_attempt_id")
    private Long quizAttemptId;

    @Column(name = "score_percent", nullable = false)
    private Double scorePercent;

    @Column(name = "certificate_number", nullable = false, length = 64, unique = true)
    private String certificateNumber;

    @Column(name = "verification_token", nullable = false, length = 64, unique = true)
    private String verificationToken;

    @Lob
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] pdfBytes;

    @Column(name = "final_engagement_score", nullable = false)
    private Double finalEngagementScore;

    @Column(name = "final_quiz_score", nullable = false)
    private Double finalQuizScore;

    @Column(name = "learner_name", length = 255)
    private String learnerName;

    @Column(name = "video_title", length = 512, nullable = false)
    private String videoTitle;

    @Column(name = "video_id", length = 32, nullable = false)
    private String videoId;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
