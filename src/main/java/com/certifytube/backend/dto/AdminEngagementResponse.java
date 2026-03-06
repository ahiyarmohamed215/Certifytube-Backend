package com.certifytube.backend.dto;

import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEngagementResponse {
    private String sessionId;
    private String model; // "xgboost" or "ebm"
    private double engagementScore;
    private double threshold;
    private String status; // "ENGAGED" or "NOT_ENGAGED"
    private String explanation;

    // Top contributors – admin only
    private Object topPositive;
    private Object topNegative;

    private Instant createdAtUtc;
}
