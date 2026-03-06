package com.certifytube.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardVideoItem {
    private String sessionId;
    private String videoId;
    private String videoTitle;
    private String thumbnailUrl;
    private Double lastPositionSec;
    private Double videoDurationSec;
    private double progressPercent;
    private String status;
    private boolean stemEligible;
    private Double engagementScore;
    private String certificateId;
    private String createdAt;
}
