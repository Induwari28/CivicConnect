package com.civicconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public record GeminiPart(String text) {}
    public record GeminiContent(List<GeminiPart> parts) {}
    public record GeminiCandidate(GeminiContent content) {}
    public record GeminiResponse(List<GeminiCandidate> candidates) {}

    public String analyzeIssue(String title, String description) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        
        // Sanitize inputs so quotes don't break our JSON
        String safeTitle = title.replace("\"", "'").replace("\n", " ");
        String safeDesc = description.replace("\"", "'").replace("\n", " ");
        String prompt = "Analyze this civic issue and respond with a single word priority (HIGH, MEDIUM, or LOW). Title: " + safeTitle + ". Description: " + safeDesc;

        // Build a raw JSON string to completely avoid Java Map compilation errors
        String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + prompt + "\"}]}]}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        try {
            GeminiResponse response = restTemplate.postForObject(url, request, GeminiResponse.class);
            if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                return response.candidates().get(0).content().parts().get(0).text().trim();
            }
            return "UNCLASSIFIED";
        } catch (Exception e) {
            System.err.println("Gemini API failed: " + e.getMessage());
            return "UNCLASSIFIED";
        }
    }
}