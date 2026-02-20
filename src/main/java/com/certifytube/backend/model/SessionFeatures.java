package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "session_features",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_features_session_id", columnNames = "session_id"),
        indexes = {
                @Index(name = "idx_session_features_session_id", columnList = "session_id")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SessionFeatures {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false, length = 128)
    private String sessionId;

    @Column(name = "contract_version", nullable = false, length = 32)
    private String contractVersion;

    // ---- Core ----
    @Column(name = "session_duration_sec", nullable = false) private Double sessionDurationSec;
    @Column(name = "video_duration_sec", nullable = false) private Double videoDurationSec;
    @Column(name = "last_position_sec", nullable = false) private Double lastPositionSec;
    @Column(name = "completed_flag", nullable = false) private Double completedFlag;

    @Column(name = "watch_time_sec", nullable = false) private Double watchTimeSec;
    @Column(name = "watch_time_ratio", nullable = false) private Double watchTimeRatio;
    @Column(name = "completion_ratio", nullable = false) private Double completionRatio;
    @Column(name = "engagement_velocity", nullable = false) private Double engagementVelocity;

    // ---- Pause ----
    @Column(name = "num_pause", nullable = false) private Double numPause;
    @Column(name = "total_pause_duration_sec", nullable = false) private Double totalPauseDurationSec;
    @Column(name = "avg_pause_duration_sec", nullable = false) private Double avgPauseDurationSec;
    @Column(name = "median_pause_duration_sec", nullable = false) private Double medianPauseDurationSec;
    @Column(name = "pause_freq_per_min", nullable = false) private Double pauseFreqPerMin;
    @Column(name = "long_pause_count", nullable = false) private Double longPauseCount;
    @Column(name = "long_pause_ratio", nullable = false) private Double longPauseRatio;

    // ---- Seek ----
    @Column(name = "num_seek", nullable = false) private Double numSeek;
    @Column(name = "num_seek_forward", nullable = false) private Double numSeekForward;
    @Column(name = "num_seek_backward", nullable = false) private Double numSeekBackward;
    @Column(name = "total_seek_forward_sec", nullable = false) private Double totalSeekForwardSec;
    @Column(name = "total_seek_backward_sec", nullable = false) private Double totalSeekBackwardSec;
    @Column(name = "avg_seek_forward_sec", nullable = false) private Double avgSeekForwardSec;
    @Column(name = "avg_seek_backward_sec", nullable = false) private Double avgSeekBackwardSec;
    @Column(name = "largest_forward_seek_sec", nullable = false) private Double largestForwardSeekSec;
    @Column(name = "largest_backward_seek_sec", nullable = false) private Double largestBackwardSeekSec;
    @Column(name = "seek_jump_std_sec", nullable = false) private Double seekJumpStdSec;

    @Column(name = "seek_forward_ratio", nullable = false) private Double seekForwardRatio;
    @Column(name = "seek_backward_ratio", nullable = false) private Double seekBackwardRatio;
    @Column(name = "skip_time_ratio", nullable = false) private Double skipTimeRatio;
    @Column(name = "rewatch_time_ratio", nullable = false) private Double rewatchTimeRatio;
    @Column(name = "rewatch_to_skip_ratio", nullable = false) private Double rewatchToSkipRatio;
    @Column(name = "seek_density_per_min", nullable = false) private Double seekDensityPerMin;

    @Column(name = "first_seek_time_sec", nullable = false) private Double firstSeekTimeSec;
    @Column(name = "early_skip_flag", nullable = false) private Double earlySkipFlag;

    // ---- Speed ----
    @Column(name = "num_ratechange", nullable = false) private Double numRatechange;
    @Column(name = "time_at_speed_lt1x_sec", nullable = false) private Double timeAtSpeedLt1xSec;
    @Column(name = "time_at_speed_1x_sec", nullable = false) private Double timeAtSpeed1xSec;
    @Column(name = "time_at_speed_gt1x_sec", nullable = false) private Double timeAtSpeedGt1xSec;

    @Column(name = "fast_ratio", nullable = false) private Double fastRatio;
    @Column(name = "slow_ratio", nullable = false) private Double slowRatio;
    @Column(name = "playback_speed_variance", nullable = false) private Double playbackSpeedVariance;
    @Column(name = "avg_playback_rate_when_playing", nullable = false) private Double avgPlaybackRateWhenPlaying;
    @Column(name = "unique_speed_levels", nullable = false) private Double uniqueSpeedLevels;

    // ---- Buffering ----
    @Column(name = "num_buffering_events", nullable = false) private Double numBufferingEvents;
    @Column(name = "buffering_time_sec", nullable = false) private Double bufferingTimeSec;
    @Column(name = "buffering_freq_per_min", nullable = false) private Double bufferingFreqPerMin;

    // ---- Composite ----
    @Column(name = "play_pause_ratio", nullable = false) private Double playPauseRatio;
    @Column(name = "attention_index", nullable = false) private Double attentionIndex;
    @Column(name = "skim_flag", nullable = false) private Double skimFlag;
    @Column(name = "deep_flag", nullable = false) private Double deepFlag;

    @CreationTimestamp
    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @UpdateTimestamp
    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;
}
