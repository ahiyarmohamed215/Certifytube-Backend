package com.certifytube.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class QuizSubmitRequest {
    @NotEmpty
    private Map<String, String> answers;
}
