package com.certifytube.backend.service;

import com.certifytube.backend.dto.EventBatchResponse;
import com.certifytube.backend.dto.EventBatchRequest;

import java.util.List;

public interface SessionEventService {
    EventBatchResponse saveBatch(List<EventBatchRequest> events);

}
