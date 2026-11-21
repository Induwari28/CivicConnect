package com.civicconnect.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicconnect.backend.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueCreationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @Test
    void createsMultipartIssueWithoutImageUsingPostgisRepository() throws Exception {
        when(geminiService.analyzeIssue("[Roads & Drainage] test", "test")).thenReturn("LOW");

        mockMvc.perform(multipart("/api/issues")
                        .param("title", "[Roads & Drainage] test")
                        .param("description", "test")
                        .param("latitude", "6.7920")
                        .param("longitude", "79.9006")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(6.792))
                .andExpect(jsonPath("$.longitude").value(79.9006))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void savesClusteredReportsWithDescriptionsLongerThan255Characters() throws Exception {
        String firstDescription = "First report: " + "a".repeat(180);
        String secondDescription = "Second report: " + "b".repeat(180);
        when(geminiService.analyzeIssue("Long report", firstDescription)).thenReturn("LOW");

        mockMvc.perform(multipart("/api/issues")
                        .param("title", "Long report")
                        .param("description", firstDescription)
                        .param("latitude", "-89.99")
                        .param("longitude", "179.99"))
                .andExpect(status().isOk());

        String aggregatedDescription = firstDescription + " | Additional report: " + secondDescription;
        mockMvc.perform(multipart("/api/issues")
                        .param("title", "Long report")
                        .param("description", secondDescription)
                        .param("latitude", "-89.99")
                        .param("longitude", "179.99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(aggregatedDescription));
    }

    @Test
    void keepsDifferentCategoriesSeparateAtTheSameLocation() throws Exception {
        String roadsTitle = "[Roads & Drainage] Blocked drain";
        String safetyTitle = "[Public Safety] Fallen wire";
        String roadsDescription = "Water is collecting at the curb.";
        String safetyDescription = "A wire is hanging over the sidewalk.";
        String additionalRoadsDescription = "The drain is overflowing.";
        when(geminiService.analyzeIssue(roadsTitle, roadsDescription)).thenReturn("LOW");
        when(geminiService.analyzeIssue(safetyTitle, safetyDescription)).thenReturn("HIGH");

        mockMvc.perform(multipart("/api/issues")
                        .param("title", roadsTitle)
                        .param("description", roadsDescription)
                        .param("latitude", "-89.95")
                        .param("longitude", "179.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(roadsTitle))
                .andExpect(jsonPath("$.reportCount").value(1));

        mockMvc.perform(multipart("/api/issues")
                        .param("title", safetyTitle)
                        .param("description", safetyDescription)
                        .param("latitude", "-89.95")
                        .param("longitude", "179.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(safetyTitle))
                .andExpect(jsonPath("$.description").value(safetyDescription))
                .andExpect(jsonPath("$.reportCount").value(1));

        mockMvc.perform(multipart("/api/issues")
                        .param("title", "[Roads & Drainage] Overflowing drain")
                        .param("description", additionalRoadsDescription)
                        .param("latitude", "-89.95")
                        .param("longitude", "179.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(roadsTitle))
                .andExpect(jsonPath("$.description")
                        .value(roadsDescription + " | Additional report: " + additionalRoadsDescription))
                .andExpect(jsonPath("$.reportCount").value(2));
    }
}
