package com.certifytube.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnalyzeResponse {
    private String sessionId;
    private String model; // "xgboost" or "ebm"
    private double engagementScore; // 0.0 – 1.0
    private double threshold; // backend-configured threshold
    private String status; // "ENGAGED" or "NOT_ENGAGED"
    private String explanation; // human-readable text from ML
}
