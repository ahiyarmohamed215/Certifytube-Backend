package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResponse {
    private String quizId;
    private String sessionId;
    private String videoId;
    private String videoTitle;
    private String difficulty;
    private int totalQuestions;
    private List<QuizQuestionDto> questions;
    private SystemFlowDto systemFlow;
}
