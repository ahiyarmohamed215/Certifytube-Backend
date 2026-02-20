package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizEligibilityResponse {
    private String sessionId;
    private boolean eligible;
    private String reason;

    private double requiredEngagementScore;
    private Double latestEngagementScore;
    private boolean engagementPassed;

    private int maxFailedAttempts;
    private int failedAttemptsUsed;
    private int remainingAttempts;
}
