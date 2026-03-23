package com.certifytube.backend.controller;

import com.certifytube.backend.dto.QuizGenerateRequest;
import com.certifytube.backend.dto.QuizEligibilityResponse;
import com.certifytube.backend.dto.QuizResponse;
import com.certifytube.backend.dto.QuizResultResponse;
import com.certifytube.backend.dto.QuizSubmitRequest;
import com.certifytube.backend.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/eligibility")
    public QuizEligibilityResponse eligibility(@RequestParam String sessionId) {
        return quizService.eligibility(sessionId);
    }

    @PostMapping("/generate")
    public QuizResponse generate(@Valid @RequestBody QuizGenerateRequest req) {
        return quizService.generate(req);
    }

    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(@PathVariable String quizId) {
        return quizService.getQuizForCurrentUser(quizId);
    }

    @PostMapping("/{quizId}/submit")
    public QuizResultResponse submit(@PathVariable String quizId, @Valid @RequestBody QuizSubmitRequest req) {
        return quizService.submit(quizId, req);
    }

    @GetMapping("/{quizId}/result")
    public QuizResultResponse result(@PathVariable String quizId) {
        return quizService.latestResult(quizId);
    }
}
