package com.certifytube.backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventBatchResponse {
    private int saved;
    private int rejected;
    @Builder.Default
    private List<EventBatchError> errors = new ArrayList<>();
}
