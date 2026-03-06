package com.certifytube.backend.dto;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartSessionResponse {
    private String sessionId;
    private boolean resumed;
    private Double lastPositionSec;
    private Double videoDurationSec;
    private boolean stemEligible;
    private String stemMessage;
}
