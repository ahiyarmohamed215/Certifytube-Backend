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
public class AdminOverviewResponse {
    private long learnerCount;
    private long sessionCount;
    private long activeSessionCount;
    private long completedSessionCount;
    private long quizPendingSessionCount;
    private long certifiedSessionCount;
    private long certificateCount;
}
