package com.certifytube.backend.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

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

        @SuppressWarnings("unchecked")
        public Map<String, Object> generateQuiz(
                        String videoId,
                        double videoDurationSec,
                        String transcript,
                        String difficulty) {

                Map<String, Object> payload = Map.of(
                                "video_id", videoId,
                                "video_duration_sec", videoDurationSec,
                                "transcript", transcript,
                                "difficulty", difficulty);

                return client()
                                .post()
                                .uri("/quiz/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(payload)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block();
        }
}
