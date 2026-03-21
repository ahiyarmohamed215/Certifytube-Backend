package com.certifytube.backend.client;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlServiceClient {

        private static final int CONNECT_TIMEOUT_MS = 5000;
        private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(25);

        @Value("${ml.base-url}")
        private String mlBaseUrl;

        private WebClient client() {
                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                                .responseTimeout(RESPONSE_TIMEOUT);
                return WebClient.builder()
                                .baseUrl(mlBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
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

                String endpoint = "/engagement/analyze/" + model;
                log.info("Calling ML engagement endpoint {} (sessionId={}, features={})", endpoint, sessionId,
                                features != null ? features.size() : 0);

                try {
                        return client()
                                        .post()
                                        .uri(endpoint)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .block();
                } catch (WebClientResponseException e) {
                        log.error("ML engagement error {} from {}{}: {}",
                                        e.getStatusCode().value(), mlBaseUrl, endpoint, e.getResponseBodyAsString());
                        throw new IllegalStateException(
                                        "ML service rejected engagement request (HTTP %d). Verify model endpoint and payload."
                                                        .formatted(e.getStatusCode().value()),
                                        e);
                } catch (WebClientRequestException e) {
                        log.error("ML engagement request failed to {}{}: {}", mlBaseUrl, endpoint, e.getMessage());
                        throw new IllegalStateException(
                                        "Cannot reach ML service at %s. Set ML_BASE_URL to a reachable ML API."
                                                        .formatted(mlBaseUrl),
                                        e);
                } catch (RuntimeException e) {
                        log.error("Unexpected ML engagement failure for {}{}: {}", mlBaseUrl, endpoint, e.getMessage());
                        throw new IllegalStateException(
                                        "ML engagement request timed out or failed unexpectedly. Check ML service health and ML_BASE_URL.",
                                        e);
                }
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
                } catch (WebClientResponseException e) {
                        log.error("ML Service quiz generate error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
                        throw e;
                } catch (WebClientRequestException e) {
                        log.error("ML service quiz generate request failed: {}", e.getMessage());
                        throw new IllegalStateException(
                                        "Cannot reach ML service at %s. Set ML_BASE_URL to a reachable ML API."
                                                        .formatted(mlBaseUrl),
                                        e);
                }
        }
}
