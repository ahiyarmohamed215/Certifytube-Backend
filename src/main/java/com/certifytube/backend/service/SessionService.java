package com.certifytube.backend.service;

import com.certifytube.backend.model.Session;

public interface SessionService {

    Session startSession(String userId, String videoId, String videoTitle);
    void endSession(String sessionId);
    Session getById(String sessionId);
}
