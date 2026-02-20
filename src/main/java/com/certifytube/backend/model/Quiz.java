package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_session_user", columnList = "session_id,user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @Column(name = "quiz_id", length = 36)
    private String quizId;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Column(name = "video_title", nullable = false, length = 512)
    private String videoTitle;

    @Column(name = "difficulty", nullable = false, length = 32)
    private String difficulty;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
