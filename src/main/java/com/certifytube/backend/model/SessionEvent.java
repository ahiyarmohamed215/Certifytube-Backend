package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Data
@Builder
@Table(
        indexes = {
                @Index(name = "idx_events_session_time", columnList = "session_id, created_at_utc")
        }
)
public class SessionEvent {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "video_id", length = 32, nullable = false)
    private String videoId;

    @Column(name = "video_title", length = 512, nullable = false)
    private String videoTitle;

    @Column(name = "event_type", length = 32, nullable = false)
    private String eventType;

    @Column(name = "player_state")
    private Integer playerState;

    @Column(name = "playback_rate")
    private Double playbackRate;

    @Column(name = "current_time_sec")
    private Double currentTimeSec;

    @Column(name = "video_duration_sec")
    private Double videoDurationSec;

    @Column(name = "created_at_utc", nullable = false)
    private LocalDateTime createdAtUtc;

    @Column(name = "client_created_at_local", length = 40)
    private String clientCreatedAtLocal;

    @Column(name = "client_tz_offset_min")
    private Integer clientTzOffsetMin;

    @Column(name = "client_event_ms")
    private Long clientEventMs;

    @Column(name = "seek_from_sec")
    private Double seekFromSec;

    @Column(name = "seek_to_sec")
    private Double seekToSec;

}
