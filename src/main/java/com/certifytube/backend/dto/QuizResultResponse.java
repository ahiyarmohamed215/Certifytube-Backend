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
public class QuizResultResponse {
    private String quizId;
    private int correctCount;
    private int totalCount;
    private double scorePercent;
    private boolean passed;
    private String certificateId;
    private String verificationLink;
    private List<QuizQuestionReviewDto> review;
}
