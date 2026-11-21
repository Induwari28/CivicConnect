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
    private static final String MODEL_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Pattern PRIORITY_PATTERN =
            Pattern.compile("\\b(HIGH|MEDIUM|LOW)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> HIGH_KEYWORDS = List.of(
            "live wire", "fallen wire", "sparking", "electrocution", "exposed cable",
            "flood", "flooding", "submerged", "collapse", "collapsed", "sinkhole",
            "fallen tree", "tree across road", "fire", "smoke", "gas leak", "explosion",
            "accident", "injury", "danger", "emergency", "hazard", "blocked road"
    );

    private static final List<String> MEDIUM_KEYWORDS = List.of(
            "pothole", "drain", "drainage", "clogged", "blocked", "streetlight",
            "street light", "lamp", "garbage", "trash", "bin", "overflowing",
            "sewage", "water main", "pipe", "traffic", "damaged road"
    );

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiService(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-2.5-flash-lite}") String model) {
        this(apiKey, model, createRestTemplate());
    }

    GeminiService(String apiKey, RestTemplate restTemplate) {
        this(apiKey, "gemini-2.5-flash-lite", restTemplate);
    }

    GeminiService(String apiKey, String model, RestTemplate restTemplate) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gemini-2.5-flash-lite" : model.trim();
        this.restTemplate = restTemplate;
        if (this.apiKey.isBlank() || this.apiKey.startsWith("YOUR_")) {
            LOGGER.warn("gemini.api.key is missing or placeholder; using rule-based urgency fallback.");
        }
    }

    public String analyzeIssue(String title, String description) {
        String safeTitle = title == null ? "" : title.trim();
        String safeDescription = description == null ? "" : description.trim();

        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return classifyByKeywords(safeTitle, safeDescription);
        }

        String prompt = """
                Classify the urgency of this municipal civic issue.
                Respond with ONLY ONE WORD: HIGH, MEDIUM, or LOW.
                - HIGH: immediate danger to life, live wires, major flooding, road collapse, fire, or severe traffic hazard.
                - MEDIUM: moderate disruption needing timely repair, such as a broken streetlight, pothole, or drainage blockage.
                - LOW: minor wear, faded paint, litter, or routine maintenance with no immediate hazard.

                Title: %s
                Description: %s
                """.formatted(safeTitle, safeDescription);

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        String endpoint = MODEL_ENDPOINT_TEMPLATE.formatted(model);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            String priority = normalizePriority(extractTextFromResponse(response.getBody()));
            if ("UNCLASSIFIED".equals(priority)) {
                LOGGER.warn("Gemini response did not contain HIGH/MEDIUM/LOW; using keyword fallback.");
                return classifyByKeywords(safeTitle, safeDescription);
            }
            return priority;
        } catch (HttpStatusCodeException exception) {
            LOGGER.error(
                    "Gemini API returned HTTP {} on model {}.",
                    exception.getStatusCode().value(),
                    model,
                    exception);
            return classifyByKeywords(safeTitle, safeDescription);
        } catch (RestClientException exception) {
            LOGGER.error("Gemini API request failed on model {}.", model, exception);
            return classifyByKeywords(safeTitle, safeDescription);
        } catch (RuntimeException exception) {
            LOGGER.error("Gemini analysis failed on model {}; using keyword fallback.", model, exception);
            return classifyByKeywords(safeTitle, safeDescription);
        }
    }

    private String classifyByKeywords(String title, String description) {
        String combined = (title + " " + description).toLowerCase(Locale.ROOT);
        for (String keyword : HIGH_KEYWORDS) {
            if (combined.contains(keyword)) {
                return "HIGH";
            }
        }
        for (String keyword : MEDIUM_KEYWORDS) {
            if (combined.contains(keyword)) {
                return "MEDIUM";
            }
        }
        return "LOW";
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