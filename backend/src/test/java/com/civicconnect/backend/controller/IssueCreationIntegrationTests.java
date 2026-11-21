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
}
