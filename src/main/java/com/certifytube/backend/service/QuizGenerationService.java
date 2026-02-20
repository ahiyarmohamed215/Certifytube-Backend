package com.certifytube.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates quiz questions using Spring AI + DeepSeek LLM.
 * Replaces the ML server's /quiz/generate endpoint.
 *
 * Question count is determined by video duration:
 * - Up to 5 min: 3 questions
 * - 5-10 min: 5 questions
 * - 10-20 min: 8 questions
 * - 20-40 min: 12 questions
 * - 40+ min: 15 questions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QuizGenerationService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate quiz questions for a video based on transcript and duration.
     *
     * @param videoId       YouTube video ID
     * @param durationSec   Video duration in seconds
     * @param transcript    Video transcript or description
     * @param difficulty    Quiz difficulty: "easy", "medium", "hard"
     * @return Map containing "questions" array with quiz data
     */
    public Map<String, Object> generateQuiz(
            String videoId,
            double durationSec,
            String transcript,
            String difficulty
    ) {
        int numQuestions = calculateQuestionCount(durationSec);
        String prompt = buildPrompt(videoId, numQuestions, transcript, difficulty);

        try {
            String response = ChatClient.create(chatModel)
                    .prompt(prompt)
                    .call()
                    .content();

            Map<String, Object> parsed = parseJsonResponse(response);
            return parsed;
        } catch (Exception e) {
            log.error("Failed to generate quiz for video {} using DeepSeek", videoId, e);
            throw new RuntimeException("Failed to generate quiz: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate number of questions based on video duration.
     */
    private int calculateQuestionCount(double durationSec) {
        int minutes = (int) (durationSec / 60);
        if (minutes <= 5) return 3;
        if (minutes <= 10) return 5;
        if (minutes <= 20) return 8;
        if (minutes <= 40) return 12;
        return 15;
    }

    /**
     * Build the prompt for DeepSeek to generate quiz questions.
     */
    private String buildPrompt(
            String videoId,
            int numQuestions,
            String transcript,
            String difficulty
    ) {
        String transcriptText = transcript == null || transcript.isBlank()
                ? "No transcript provided. Generate questions about general knowledge for video ID: " + videoId
                : transcript;

        String difficultyDesc = switch (difficulty.toLowerCase()) {
            case "easy" -> "for beginners, testing basic understanding";
            case "hard" -> "advanced, testing deep understanding and application";
            default -> "moderate, testing understanding and recall";
        };

        return """
                You are an expert educational content creator. Generate a quiz with EXACTLY %d questions about the following video content.

                The questions should be %s

                Video Transcript / Description:
                %s

                IMPORTANT:
                1. Mix question types: Include MCQ (multiple choice), True/False, and short answer/code questions
                2. Each question should test understanding of the video content
                3. MCQ questions must have exactly 4 options labeled as strings (e.g., "a) Option 1", "b) Option 2", etc.)
                4. Make sure questions are clear, concise, and educational

                Return ONLY valid JSON with this exact structure (no markdown, no explanations):
                {
                  "questions": [
                    {
                      "type": "mcq",
                      "question": "What is...",
                      "options": ["a) First option", "b) Second option", "c) Third option", "d) Fourth option"],
                      "answer": "b) Second option",
                      "explanation": "Explanation of the correct answer"
                    },
                    {
                      "type": "true_false",
                      "question": "Is the statement true?",
                      "options": ["True", "False"],
                      "answer": "True",
                      "explanation": "Explanation of why this is true"
                    },
                    {
                      "type": "short_answer",
                      "question": "What does X do?",
                      "options": [],
                      "answer": "The answer text (will be matched case-insensitive)",
                      "explanation": "Explanation of the concept"
                    }
                  ]
                }
                """.formatted(numQuestions, difficultyDesc, transcriptText);
    }

    /**
     * Parse JSON response from DeepSeek, extracting the questions array.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String response) throws Exception {
        // Try to find and extract JSON from the response
        String jsonStr = extractJsonFromResponse(response);

        Map<String, Object> result = objectMapper.readValue(jsonStr, Map.class);

        // Validate that questions is a list
        Object questionsRaw = result.get("questions");
        if (!(questionsRaw instanceof List)) {
            throw new IllegalArgumentException("Response does not contain 'questions' array");
        }

        List<Object> questions = (List<Object>) questionsRaw;
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No questions generated");
        }

        return result;
    }

    /**
     * Extract JSON object from response text (handles cases where LLM adds markdown formatting).
     */
    private String extractJsonFromResponse(String response) {
        // If response contains markdown code blocks, extract the JSON
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.lastIndexOf("```");
            if (end > start) {
                return response.substring(start, end).trim();
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.lastIndexOf("```");
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // If it starts with { or [, assume it's already JSON
        String trimmed = response.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        // Try to find JSON in the response
        int jsonStart = response.indexOf("{");
        if (jsonStart >= 0) {
            // Find the matching closing brace
            int braceCount = 0;
            for (int i = jsonStart; i < response.length(); i++) {
                if (response.charAt(i) == '{') braceCount++;
                if (response.charAt(i) == '}') braceCount--;
                if (braceCount == 0) {
                    return response.substring(jsonStart, i + 1);
                }
            }
        }

        throw new IllegalArgumentException("Could not extract JSON from response: " + response);
    }
}
