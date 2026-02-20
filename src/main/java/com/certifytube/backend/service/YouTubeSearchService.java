package com.certifytube.backend.service;

import com.certifytube.backend.dto.YouTubeSearchResponse;

public interface YouTubeSearchService {
    YouTubeSearchResponse searchVideos(String query, int requestedLimit);

    /**
     * Search for STEM videos (Science, Technology, Engineering, Math).
     * YouTube Category IDs:
     * - 27 = Science & Technology
     * - 28 = Science & Technology (Gaming - but also educational)
     * - 30 = Movies
     * - 32 = Education (more educational focused)
     */
    YouTubeSearchResponse searchStemVideos(String query, int requestedLimit);
}
