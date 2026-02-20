package com.certifytube.backend.client;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class YouTubeClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);

    private final Environment environment;

    @Value("${youtube.base-url}")
    private String youtubeBaseUrl;

    @Value("${youtube.api-key:}")
    private String youtubeApiKey;

    @PostConstruct
    void logApiKeySource() {
        String envKey = environment.getProperty("YOUTUBE_API_KEY");
        String source = (envKey != null && !envKey.isBlank()) ? "env:YOUTUBE_API_KEY" : "application.properties";
        String key = youtubeApiKey == null ? "" : youtubeApiKey.trim();
        log.info("YouTube API key source: {}, key: {}", source, maskKey(key));
    }

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(youtubeBaseUrl.trim())
                .build();
    }

    public String searchVideos(String query, int maxResults) {
        String key = youtubeApiKey == null ? "" : youtubeApiKey.trim();
        if (key.isBlank()) {
            throw new IllegalStateException("youtube.api-key is missing");
        }

        String url = UriComponentsBuilder.fromPath("/search")
                .queryParam("part", "snippet")
                .queryParam("type", "video")
                .queryParam("order", "relevance")
                .queryParam("q", query)
                .queryParam("maxResults", maxResults)
                .queryParam("key", key)
                .encode()
                .build()
                .toUriString();

        return performGet(url);
    }

    public String fetchVideoById(String videoId) {
        String key = youtubeApiKey == null ? "" : youtubeApiKey.trim();
        if (key.isBlank()) {
            throw new IllegalStateException("youtube.api-key is missing");
        }

        String url = UriComponentsBuilder.fromPath("/videos")
                .queryParam("part", "snippet")
                .queryParam("id", videoId)
                .queryParam("key", key)
                .encode()
                .build()
                .toUriString();

        return performGet(url);
    }

    private String performGet(String url) {
        return client()
                .get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("YouTube API error: " + body))))
                .bodyToMono(String.class)
                .block();
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "<empty>";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
