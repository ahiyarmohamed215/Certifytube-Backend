package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminLearnerProfileResponse {
    private AdminUserSummaryDto learner;
    private List<SessionInsight> sessions;
    private List<QuizInsight> quizzes;
    private List<CertificateInsight> certificates;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionInsight {
        private String sessionId;
        private String userId;
        private String videoId;
        private String videoTitle;
        private String status;
        private LocalDateTime createdAtUtc;
        private LocalDateTime endedAtUtc;
        private Double lastPositionSec;
        private Double videoDurationSec;
        private Map<String, Object> features;
        private AdminEngagementResponse engagement;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizInsight {
        private String quizId;
        private String sessionId;
        private String videoId;
        private String videoTitle;
        private String difficulty;
        private Integer totalQuestions;
        private LocalDateTime createdAtUtc;
        private QuizAttemptInsight latestAttempt;
        private List<QuizQuestionInsight> questions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizAttemptInsight {
        private Long attemptId;
        private Integer correctCount;
        private Integer totalCount;
        private Double scorePercent;
        private Boolean passedFlag;
        private Object answers;
        private LocalDateTime createdAtUtc;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizQuestionInsight {
        private Long id;
        private String questionUid;
        private Integer positionIndex;
        private String questionType;
        private String questionText;
        private Object options;
        private String correctAnswer;
        private String explanationText;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CertificateInsight {
        private String certificateId;
        private String certificateNumber;
        private String sessionId;
        private Long quizAttemptId;
        private Double scorePercent;
        private Double finalEngagementScore;
        private Double finalQuizScore;
        private String learnerName;
        private String videoTitle;
        private String videoId;
        private String status;
        private LocalDateTime createdAtUtc;
    }

}
