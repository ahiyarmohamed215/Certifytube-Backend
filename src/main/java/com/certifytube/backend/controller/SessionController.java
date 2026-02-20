package com.certifytube.backend.controller;

import com.certifytube.backend.dto.StartSessionRequest;
import com.certifytube.backend.dto.StartSessionResponse;
import com.certifytube.backend.dto.EndSessionResponse;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {

    private final SessionService sessionService;
    private final AuthenticatedUserService authenticatedUserService;

    public SessionController(SessionService sessionService, AuthenticatedUserService authenticatedUserService) {
        this.sessionService = sessionService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping({"/api/sessions/start", "/start_session"})
    public StartSessionResponse startSession(@Valid @RequestBody StartSessionRequest req) {
        UserAccount user = authenticatedUserService.currentUser();
        Session s = sessionService.startSession(
                String.valueOf(user.getId()),
                req.getVideoId(),
                req.getVideoTitle()
        );
        return new StartSessionResponse(s.getSessionId());
    }

    @PostMapping({"/api/sessions/end", "/end_session"})
    public EndSessionResponse endSession(@RequestParam String sessionId) {
        UserAccount user = authenticatedUserService.currentUser();
        Session session = sessionService.getById(sessionId);
        if (!String.valueOf(user.getId()).equals(session.getUserId())) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }
        sessionService.endSession(sessionId);
        return new EndSessionResponse(true);
    }
}
