package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "youtube_video_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_youtube_video_cache_video_id", columnNames = "video_id"),
        indexes = {
                @Index(name = "idx_youtube_video_cache_video_id", columnList = "video_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YouTubeVideoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "channel_title", nullable = false, length = 256)
    private String channelTitle;

    @Lob
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    @Column(name = "published_at", length = 64)
    private String publishedAt;

    @Column(name = "iframe_url", nullable = false, length = 1024)
    private String iframeUrl;

    @Column(name = "category_id", length = 32)
    private String categoryId;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;
}
