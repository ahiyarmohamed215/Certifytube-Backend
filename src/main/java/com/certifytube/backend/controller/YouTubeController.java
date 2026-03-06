package com.certifytube.backend.controller;

import com.certifytube.backend.dto.YouTubeSearchResponse;
import com.certifytube.backend.service.YouTubeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/youtube")
public class YouTubeController {

    private final YouTubeSearchService youTubeSearchService;

    @GetMapping("/search")
    public YouTubeSearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return youTubeSearchService.searchVideos(query, limit);
    }
}
