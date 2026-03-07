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
public class CertificateResponse {
    private String certificateId;
    private String certificateNumber;
    private String sessionId;
    private Long userId; // Will be null in public verify response
    private double scorePercent;
    
    // New fields
    private String learnerName;
    private String videoTitle;
    private String videoId;
    private String videoUrl;
    private Double engagementScore;
    private Double quizScore;
    private Double engagementThreshold;
    private Double quizThreshold;
    private String platformName;
    private String platformAttribution;

    private String verificationToken;
    private String verificationLink;
    private String createdAtUtc;
}
