package com.civicconnect.backend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final Pattern PRIORITY_PATTERN =
            Pattern.compile("\\b(HIGH|MEDIUM|LOW)\\b", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final String apiKey;

    @Autowired
    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        this(apiKey, createRestTemplate());
    }

    GeminiService(String apiKey, RestTemplate restTemplate) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restTemplate = restTemplate;
        if (this.apiKey.isBlank() || this.apiKey.startsWith("YOUR_")) {
            LOGGER.warn("gemini.api.key is missing or still set to a placeholder; triage will be unclassified.");
        }
    }

    public String analyzeIssue(String title, String description) {
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return "UNCLASSIFIED";
        }

        String prompt = """
                Classify the urgency of this municipal civic issue.
                Respond with ONLY ONE WORD: HIGH, MEDIUM, or LOW.
                - HIGH: immediate danger to life, live wires, major flooding, road collapse, or severe traffic hazard.
                - MEDIUM: moderate disruption needing timely repair, such as a broken streetlight or drainage blockage.
                - LOW: minor wear, litter, or routine maintenance with no immediate hazard.

                Title: %s
                Description: %s
                """.formatted(title, description);
        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    GEMINI_URL,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            String priority = normalizePriority(extractTextFromResponse(response.getBody()));
            if ("UNCLASSIFIED".equals(priority)) {
                LOGGER.warn("Gemini response did not contain a recognized priority.");
            }
            return priority;
        } catch (HttpStatusCodeException exception) {
            LOGGER.error(
                    "Gemini API returned HTTP {}: {}",
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString());
            return "UNCLASSIFIED";
        } catch (RestClientException exception) {
            LOGGER.error("Gemini API request failed: {}", exception.getMessage(), exception);
            return "UNCLASSIFIED";
        }
    }

    private String extractTextFromResponse(Map<?, ?> body) {
        if (body == null || !(body.get("candidates") instanceof List<?> candidates)) {
            return null;
        }

        List<String> texts = new ArrayList<>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof Map<?, ?> candidateMap)
                    || !(candidateMap.get("content") instanceof Map<?, ?> contentMap)
                    || !(contentMap.get("parts") instanceof List<?> parts)) {
                continue;
            }
            for (Object part : parts) {
                if (part instanceof Map<?, ?> partMap && partMap.get("text") instanceof String text) {
                    texts.add(text);
                }
            }
        }
        return texts.isEmpty() ? null : String.join(" ", texts);
    }

    private String normalizePriority(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "UNCLASSIFIED";
        }
        Matcher matcher = PRIORITY_PATTERN.matcher(rawText.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : "UNCLASSIFIED";
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(requestFactory);
    }
}
