package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(
        name = "youtube_search_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_youtube_search_cache_query", columnNames = "query_text"),
        indexes = {
                @Index(name = "idx_youtube_search_cache_query", columnList = "query_text")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YouTubeSearchCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, length = 512)
    private String queryText;

    @Column(name = "last_refreshed_on", nullable = false)
    private LocalDate lastRefreshedOn;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private LocalDateTime createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private LocalDateTime updatedAtUtc;
}
