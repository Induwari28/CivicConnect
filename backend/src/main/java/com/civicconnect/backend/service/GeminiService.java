package com.civicconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String analyzeIssue(String title, String description) {
        String url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        
        String safeTitle = title.replace("\"", "'").replace("\n", " ");
        String safeDesc = description.replace("\"", "'").replace("\n", " ");
        String prompt = "Analyze this civic issue and respond with ONLY a single word priority (HIGH, MEDIUM, or LOW). Title: " + safeTitle + ". Description: " + safeDesc;

        String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + prompt + "\"}]}]}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        try {
            String rawResponse = restTemplate.postForObject(url, request, String.class);
            
            // Print exactly what Google sends back to the terminal
            System.out.println("\n--- RAW GEMINI RESPONSE ---");
            System.out.println(rawResponse);
            System.out.println("---------------------------\n");
            
            // Bulletproof Regex extraction
            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(rawResponse);
            
            if (matcher.find()) {
                String text = matcher.group(1).toUpperCase();
                if (text.contains("HIGH")) return "HIGH";
                if (text.contains("MEDIUM")) return "MEDIUM";
                if (text.contains("LOW")) return "LOW";
            }
            
            return "UNCLASSIFIED";
            
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value() + "_ERROR";
        } catch (Exception e) {
            return "API_ERROR";
        }
    }
}