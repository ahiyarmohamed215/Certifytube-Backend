package com.certifytube.backend.service;

import com.certifytube.backend.dto.YouTubeSearchResponse;

public interface YouTubeSearchService {
    YouTubeSearchResponse searchVideos(String query, int requestedLimit);

    /**
     * Search for STEM videos.
     * YouTube Category IDs:
     * - 26 = How-to & Style
     * - 27 = Education
     * - 28 = Science & Technology
     */
    YouTubeSearchResponse searchStemVideos(String query, int requestedLimit);
}
