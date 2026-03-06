package com.certifytube.backend.controller;

import com.certifytube.backend.dto.EventBatchRequest;
import com.certifytube.backend.dto.EventBatchResponse;
import com.certifytube.backend.service.SessionEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventBatchController {

    private final SessionEventService sessionEventService;

    @PostMapping("/batch")
    public EventBatchResponse logBatch(
            @RequestBody List<EventBatchRequest> events
    ) {
        log.info("Received batch of {} events", events != null ? events.size() : 0);
        EventBatchResponse response = sessionEventService.saveBatch(events);
        log.info("Batch response: saved={}, rejected={}", response.getSaved(), response.getRejected());
        return response;
    }
}
