package com.certifytube.backend.controller;

import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.service.SessionAnalyzeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionAnalyzeController {

    private final SessionAnalyzeService sessionAnalyzeService;

    @PostMapping("/{sessionId}/analyze")
    public SessionAnalyzeResponse analyze(
            @PathVariable String sessionId,
            @RequestParam(required = false) String model) {
        return sessionAnalyzeService.analyzeSession(sessionId, model);
    }
}
