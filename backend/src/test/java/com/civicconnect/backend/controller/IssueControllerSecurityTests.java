package com.civicconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.civicconnect.backend.model.Issue;
import com.civicconnect.backend.repository.IssueRepository;
import com.civicconnect.backend.service.GeminiService;
import com.civicconnect.backend.service.IssueImageStorageService;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.admin.username=test-admin",
        "app.admin.password=test-password"
})
class IssueControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueRepository issueRepository;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private IssueImageStorageService imageStorageService;

    @Test
    void adminRoutesRequireAuthenticationButPublicIssueFeedDoesNot() throws Exception {
        mockMvc.perform(get("/api/issues/admin"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/issues/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isOk());
    }

    @Test
    void adminPatchPreflightAllowsFrontendAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/issues/7/status")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void authenticatedAdminCanUpdateIssueStatus() throws Exception {
        Issue issue = new Issue();
        issue.setId(7L);
        issue.setTitle("Broken streetlight");
        issue.setDescription("The light is not working.");
        issue.setPriority("MEDIUM");
        Point location = new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(79.9, 6.8));
        issue.setLocation(location);

        when(issueRepository.findById(7L)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/issues/7/status")
                        .header("Authorization", basicAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void acceptsMultipartIssueWithAnAttachedImage() throws Exception {
        byte[] pngHeader = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile image = new MockMultipartFile("image", "street.png", "image/png", pngHeader);
        when(geminiService.analyzeIssue("Pothole", "Large hole in the road.")).thenReturn("HIGH");
        when(imageStorageService.store(any(MultipartFile.class))).thenReturn("stored-image.png");
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue savedIssue = invocation.getArgument(0);
            savedIssue.setId(8L);
            return savedIssue;
        });

        mockMvc.perform(multipart("/api/issues")
                        .file(image)
                        .param("title", "Pothole")
                        .param("description", "Large hole in the road.")
                        .param("latitude", "6.8")
                        .param("longitude", "79.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.imageUrl").value("/uploads/stored-image.png"));

        verify(imageStorageService).store(any(MultipartFile.class));
    }

    @Test
    void acceptsJsonIssueSubmissionsWithoutAnImage() throws Exception {
        when(geminiService.analyzeIssue("Pothole", "Large hole in the road.")).thenReturn("HIGH");
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue savedIssue = invocation.getArgument(0);
            savedIssue.setId(9L);
            return savedIssue;
        });

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Pothole",
                                  "description": "Large hole in the road.",
                                  "latitude": 6.8,
                                  "longitude": 79.9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    private String basicAuthorization() {
        String credentials = "test-admin:test-password";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
    }
}
