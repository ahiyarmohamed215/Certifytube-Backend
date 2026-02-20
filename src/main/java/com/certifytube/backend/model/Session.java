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

    public Session() {}

    public Session(String sessionId, String userId, String videoId, String videoTitle, Instant createdAtUtc) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.createdAtUtc = createdAtUtc;
    }

}
