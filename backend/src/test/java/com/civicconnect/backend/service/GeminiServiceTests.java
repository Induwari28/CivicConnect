package com.civicconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GeminiServiceTests {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    @Test
    void parsesPriorityFromCandidatePartsAndSendsApiKeyHeader() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GeminiService service = new GeminiService("test-key", restTemplate);

        server.expect(requestTo(GEMINI_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [{
                            "content": {
                              "parts": [
                                {"text": "MEDIUM"},
                                {"text": "Priority"}
                              ]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertEquals("MEDIUM", service.analyzeIssue("Pothole", "Road damage."));
        server.verify();
    }

    @Test
    void fallsBackToValidPriorityWhenGeminiRespondsWithAnHttpError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GeminiService service = new GeminiService("test-key", restTemplate);

        server.expect(requestTo(GEMINI_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"invalid API key\"}}"));

        assertEquals("MEDIUM", service.analyzeIssue("Pothole", "Road damage."));
        server.verify();
    }

    @Test
    void fallsBackToValidPriorityWhenTheGeminiRequestTimesOut() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GeminiService service = new GeminiService("test-key", restTemplate);

        server.expect(requestTo(GEMINI_URL))
                .andRespond(withException(new IOException("connect timed out")));

        assertEquals("MEDIUM", service.analyzeIssue("Pothole", "Road damage."));
        server.verify();
    }

    @Test
    void usesRuleBasedPriorityWhenApiKeyIsMissing() {
        GeminiService service = new GeminiService("", new RestTemplate());

        assertEquals("HIGH", service.analyzeIssue("Exposed cable", "Sparking near the road."));
    }

    @Test
    void fallsBackWhenGeminiReturnsNoRecognizedPriority() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GeminiService service = new GeminiService("test-key", restTemplate);

        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"urgent"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("LOW", service.analyzeIssue("Faded paint", "Routine maintenance."));
        server.verify();
    }
}
