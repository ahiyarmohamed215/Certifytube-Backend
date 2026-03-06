package com.certifytube.backend.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlServiceClient {

        @Value("${ml.base-url}")
        private String mlBaseUrl;

        private WebClient client() {
                return WebClient.builder()
                                .baseUrl(mlBaseUrl)
                                .build();
        }

        /**
         * Call the ML engagement analysis endpoint.
         *
         * @param sessionId      session identifier
         * @param featureVersion feature contract version (e.g. "v1.0")
         * @param features       exactly the 49 features from the contract
         * @param model          "xgboost" or "ebm"
         * @return raw ML response as a Map
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> analyzeEngagement(
                        String sessionId,
                        String featureVersion,
                        Map<String, Object> features,
                        String model) {

                Map<String, Object> payload = Map.of(
                                "session_id", sessionId,
                                "feature_version", featureVersion,
                                "features", features);

                return client()
                                .post()
                                .uri("/engagement/analyze/" + model)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(payload)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block();
        }

        /**
         * Call the ML quiz generation endpoint.
         * ML service handles transcript fetching, caching, and quiz generation.
         *
         * @param sessionId        session identifier
         * @param videoId          YouTube video ID
         * @param videoDurationSec video duration in seconds
         * @param videoTitle       optional video title for context
         * @param numQuestions     optional manual override (1-20)
         * @param includeCoding    optional flag for coding questions
         * @return raw ML response as a Map
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> generateQuiz(
                        String sessionId,
                        String videoId,
                        double videoDurationSec,
                        String videoTitle,
                        Integer numQuestions,
                        Boolean includeCoding) {

                Map<String, Object> payload = new HashMap<>();
                payload.put("session_id", sessionId);
                payload.put("video_id", videoId);
                payload.put("video_duration_sec", videoDurationSec);

                log.info("Sending payload to ML service /quiz/generate: {}", payload);

                try {
                        return client()
                                        .post()
                                        .uri("/quiz/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .block();
                } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                        log.error("ML Service quiz generate error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
                        throw e;
                }
        }
}
