package com.certifytube.backend.controller;

import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.service.SessionAnalyzeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionAnalyzeController {

    private final SessionAnalyzeService sessionAnalyzeService;

    @PostMapping("/{sessionId}/analyze")
    public SessionAnalyzeResponse analyze(
            @PathVariable String sessionId,
            @RequestParam(required = false) String model) {
        log.info("Received analyze request for session={}, model={}", sessionId, model);
        SessionAnalyzeResponse response = sessionAnalyzeService.analyzeSession(sessionId, model);
        log.info("Analyze response for session={}: status={}, score={}", sessionId, response.getStatus(), response.getEngagementScore());
        return response;
    }
}
