package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@AllArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "video_id", length = 32, nullable = false)
    private String videoId;

    @Column(name = "video_title", length = 512, nullable = false)
    private String videoTitle;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    @Column(name = "ended_at_utc")
    private Instant endedAtUtc;

    /** Last known playback position in seconds (for resume). */
    @Column(name = "last_position_sec")
    private Double lastPositionSec;

    /** Total video duration in seconds. */
    @Column(name = "video_duration_sec")
    private Double videoDurationSec;

    /**
     * Session lifecycle status: ACTIVE, COMPLETED, QUIZ_PENDING, CERTIFIED.
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    public Session() {
        this.status = "ACTIVE";
    }

    public Session(String sessionId, String userId, String videoId, String videoTitle, Instant createdAtUtc) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.createdAtUtc = createdAtUtc;
        this.status = "ACTIVE";
    }

}
