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
public class QuizQuestionReviewDto {
    private String questionId;
    private String questionType;
    private String questionText;
    private List<String> options;
    private String selectedAnswer;
    private String correctAnswer;
    private boolean correct;
    private String explanation;
}
