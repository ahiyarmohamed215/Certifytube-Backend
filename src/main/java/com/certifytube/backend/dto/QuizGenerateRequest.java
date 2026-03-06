package com.certifytube.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizGenerateRequest {

    @NotBlank
    private String sessionId;

    @Size(max = 32)
    private String difficulty;

    @JsonAlias({ "num_questions" })
    private Integer numQuestions;

    @JsonAlias({ "include_coding" })
    private Boolean includeCoding;
}
