package com.certifytube.backend.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EventBatchRequest {

    private String sessionId;
    private String userId;
    private String videoId;
    private String videoTitle;

    private String eventType;
    private Integer playerState;
    private Double playbackRate;
    private Double currentTimeSec;
    private Double videoDurationSec;

    private String clientCreatedAtLocal;
    private Integer clientTzOffsetMin;
    private Long clientEventMs;

    private Double seekFromSec;
    private Double seekToSec;
}
