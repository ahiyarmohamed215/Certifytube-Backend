package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "engagement_results", indexes = {
        @Index(name = "idx_results_session_id", columnList = "sessionId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngagementResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 16)
    private String modelUsed;

    private Double engagementScore;
    private Double threshold;

    @Column(nullable = false, length = 16)
    private String status;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String explanation;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String topPositiveJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String topNegativeJson;

    @Column(nullable = false)
    private LocalDateTime createdAtUtc;
}
