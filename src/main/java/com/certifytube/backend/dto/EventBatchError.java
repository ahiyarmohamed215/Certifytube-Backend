package com.certifytube.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventBatchError {
    private int index;
    private String message;
}
