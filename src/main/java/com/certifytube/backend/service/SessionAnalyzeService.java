package com.certifytube.backend.service;

import com.certifytube.backend.dto.SessionAnalyzeResponse;

public interface SessionAnalyzeService {
    SessionAnalyzeResponse analyzeSession(String sessionId, String model);
}
