package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "youtube_search_cache_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_youtube_cache_item_cache_pos", columnNames = {"cache_id", "position_index"}),
                @UniqueConstraint(name = "uk_youtube_cache_item_cache_video", columnNames = {"cache_id", "video_cache_id"})
        },
        indexes = {
                @Index(name = "idx_youtube_cache_item_cache", columnList = "cache_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YouTubeSearchCacheItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cache_id", nullable = false)
    private YouTubeSearchCache cache;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "video_cache_id", nullable = false)
    private YouTubeVideoCache video;

    @Column(name = "position_index", nullable = false)
    private Integer positionIndex;
}
