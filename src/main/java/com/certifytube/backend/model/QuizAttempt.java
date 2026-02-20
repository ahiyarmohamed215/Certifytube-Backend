package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_quiz_attempts_quiz_user", columnList = "quiz_id,user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "answers_json", nullable = false, columnDefinition = "LONGTEXT")
    private String answersJson;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "score_percent", nullable = false)
    private Double scorePercent;

    @Column(name = "passed_flag", nullable = false)
    private Boolean passedFlag;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
