package com.certifytube.backend.controller;

import com.certifytube.backend.dto.QuizGenerateRequest;
import com.certifytube.backend.dto.QuizEligibilityResponse;
import com.certifytube.backend.dto.QuizResponse;
import com.certifytube.backend.dto.QuizResultResponse;
import com.certifytube.backend.dto.QuizSubmitRequest;
import com.certifytube.backend.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/eligibility")
    public QuizEligibilityResponse eligibility(@RequestParam String sessionId) {
        log.info("Checking quiz eligibility for session={}", sessionId);
        QuizEligibilityResponse response = quizService.eligibility(sessionId);
        log.info("Eligibility response for session={}: eligible={}", sessionId, response.isEligible());
        return response;
    }

    @PostMapping("/generate")
    public QuizResponse generate(@Valid @RequestBody QuizGenerateRequest req) {
        log.info("Generating quiz for session={}, difficulty={}", req.getSessionId(), req.getDifficulty());
        QuizResponse response = quizService.generate(req);
        log.info("Generated quiz={} for session={}", response.getQuizId(), req.getSessionId());
        return response;
    }

    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(@PathVariable String quizId) {
        return quizService.getQuizForCurrentUser(quizId);
    }

    @PostMapping("/{quizId}/submit")
    public QuizResultResponse submit(@PathVariable String quizId, @Valid @RequestBody QuizSubmitRequest req) {
        log.info("Submitting quiz={} with {} answers", quizId, req.getAnswers().size());
        QuizResultResponse response = quizService.submit(quizId, req);
        log.info("Quiz={} submitted, passed={}, score={}", quizId, response.isPassed(), response.getScorePercent());
        return response;
    }

    @GetMapping("/{quizId}/result")
    public QuizResultResponse result(@PathVariable String quizId) {
        return quizService.latestResult(quizId);
    }
}
