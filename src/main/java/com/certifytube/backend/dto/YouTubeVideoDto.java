package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YouTubeVideoDto {
    private String videoId;
    private String title;
    private String channelTitle;
    private String description;
    private String thumbnailUrl;
    private String publishedAt;
    private String iframeUrl;
    private String categoryId;  // YouTube category ID (e.g., "28" for Science & Tech)
}
