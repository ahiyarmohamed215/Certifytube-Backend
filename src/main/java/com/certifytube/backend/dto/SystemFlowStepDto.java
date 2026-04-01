package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemFlowStepDto {
    private String step;
    private String status;
    private String message;

    @Builder.Default
    private Map<String, Object> data = new LinkedHashMap<>();
}
