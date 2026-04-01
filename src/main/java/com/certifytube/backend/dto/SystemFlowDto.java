package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemFlowDto {
    private String feature;
    private String requestId;

    @Builder.Default
    private List<SystemFlowStepDto> steps = new ArrayList<>();
}
