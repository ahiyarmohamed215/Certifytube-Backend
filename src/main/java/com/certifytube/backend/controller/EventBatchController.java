package com.certifytube.backend.controller;

import com.certifytube.backend.dto.EventBatchRequest;
import com.certifytube.backend.dto.EventBatchResponse;
import com.certifytube.backend.service.SessionEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventBatchController {

    private final SessionEventService sessionEventService;

    @PostMapping("/batch")
    public EventBatchResponse logBatch(
            @RequestBody List<EventBatchRequest> events
    ) {
        return sessionEventService.saveBatch(events);
    }
}
