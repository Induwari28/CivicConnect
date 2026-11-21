package com.civicconnect.backend.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
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
import org.springframework.web.multipart.MultipartFile;

@Service
public class GeminiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiService.class);
    private static final String MODEL_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Pattern PRIORITY_PATTERN =
            Pattern.compile("\\b(HIGH|MEDIUM|LOW)\\b", Pattern.CASE_INSENSITIVE);
    private static final long MAX_INLINE_IMAGE_BYTES = 15L * 1024L * 1024L;

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
            @Value("${gemini.api.model:gemini-3.5-flash-lite}") String model) {
        this(apiKey, model, createRestTemplate());
    }

    GeminiService(String apiKey, RestTemplate restTemplate) {
        this(apiKey, "gemini-3.5-flash-lite", restTemplate);
    }

    GeminiService(String apiKey, String model, RestTemplate restTemplate) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gemini-3.5-flash-lite" : model.trim();
        this.restTemplate = restTemplate;
        if (this.apiKey.isBlank() || this.apiKey.startsWith("YOUR_")) {
            LOGGER.warn("gemini.api.key is missing or placeholder; using fallback priority classification.");
        }
    }

    public String analyzeIssue(String title, String description) {
        return analyzeIssue(title, description, null);
    }

    public String analyzeIssue(String title, String description, MultipartFile image) {
        String safeTitle = title == null ? "" : title.trim();
        String safeDescription = description == null ? "" : description.trim();

        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return classifyFallback(safeTitle, safeDescription, image);
        }

        String prompt = """
                Classify the urgency of this municipal civic issue using both the text and any attached photo.
                Respond with ONLY ONE WORD: HIGH, MEDIUM, or LOW.
- HIGH: immediate danger to life, live/sparking wires, deep flooding, road/structure collapse, fire, or severe hazard visible in text or photo.
- MEDIUM: moderate infrastructure or service disruption needing timely repair, such as a pothole, broken streetlight, drainage blockage, or overflowing bin.
- LOW: minor wear, faded paint, minor litter, or routine maintenance with no immediate hazard.

                Title: %s
                Description: %s
                """.formatted(safeTitle, safeDescription);

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        if (image != null && !image.isEmpty()) {
            try {
                byte[] bytes = image.getBytes();
                if (bytes.length > 0 && bytes.length <= MAX_INLINE_IMAGE_BYTES) {
                    String mimeType = image.getContentType();
                    if (mimeType == null || mimeType.isBlank() || !mimeType.startsWith("image/")) {
                        mimeType = "image/jpeg";
                    }
                    String base64Data = Base64.getEncoder().encodeToString(bytes);
                    Map<String, Object> inlineData = new LinkedHashMap<>();
                    inlineData.put("mime_type", mimeType);
                    inlineData.put("data", base64Data);
                    parts.add(Map.of("inline_data", inlineData));
                }
            } catch (IOException ex) {
                LOGGER.warn("Could not read uploaded image bytes for Gemini analysis; continuing with text.", ex);
            }
        }

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", parts)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        String endpoint = MODEL_ENDPOINT_TEMPLATE.formatted(model);

        try {
            ResponseEntity<?> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            Object body = response.getBody();
            String priority = normalizePriority(
                    body instanceof Map<?, ?> responseBody ? extractTextFromResponse(responseBody) : null);
            if ("UNCLASSIFIED".equals(priority)) {
                LOGGER.warn("Gemini response did not return HIGH/MEDIUM/LOW; using fallback.");
                return classifyFallback(safeTitle, safeDescription, image);
            }
            return priority;
        } catch (HttpStatusCodeException exception) {
            LOGGER.error(
                    "Gemini API returned HTTP {} on model {}: {}",
                    exception.getStatusCode().value(),
                    model,
                    exception.getResponseBodyAsString());
            return classifyFallback(safeTitle, safeDescription, image);
        } catch (RestClientException exception) {
            LOGGER.error("Gemini API request failed on model {}: {}", model, exception.getMessage());
            return classifyFallback(safeTitle, safeDescription, image);
        } catch (RuntimeException exception) {
            LOGGER.error("Gemini analysis failed on model {}; using fallback.", model, exception);
            return classifyFallback(safeTitle, safeDescription, image);
        }
    }

    private String classifyFallback(String title, String description, MultipartFile image) {
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
        if (image != null && !image.isEmpty()) {
            return "MEDIUM";
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
        requestFactory.setReadTimeout(Duration.ofSeconds(25));
        return new RestTemplate(requestFactory);
    }
}