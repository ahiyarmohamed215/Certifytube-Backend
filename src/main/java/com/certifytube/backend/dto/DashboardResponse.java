package com.certifytube.backend.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private List<DashboardVideoItem> activeVideos;
    private List<DashboardVideoItem> completedVideos;
    private List<DashboardVideoItem> quizPendingVideos;
    private List<DashboardVideoItem> certifiedVideos;
}
