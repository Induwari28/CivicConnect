package com.civicconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

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
    void publicIssueFeedReturnsCoordinatesInsteadOfSerializingGeometry() throws Exception {
        Issue issue = new Issue();
        issue.setId(3L);
        issue.setTitle("Broken streetlight");
        issue.setDescription("The light is not working.");
        issue.setStatus("PENDING");
        issue.setPriority("MEDIUM");
        issue.setLocation(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(79.9, 6.8)));
        when(issueRepository.findAll()).thenReturn(List.of(issue));

        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latitude").value(6.8))
                .andExpect(jsonPath("$[0].longitude").value(79.9))
                .andExpect(jsonPath("$[0].location").doesNotExist());
    }

    @Test
    void publicIssueFeedSkipsRowsWithMissingOrInvalidLocations() throws Exception {
        Issue missingLocation = new Issue();
        missingLocation.setId(4L);

        Issue invalidCoordinates = new Issue();
        invalidCoordinates.setId(5L);
        invalidCoordinates.setLocation(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(181, 6.8)));

        when(issueRepository.findAll()).thenReturn(List.of(missingLocation, invalidCoordinates));

        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void preflightAllowsFrontendMethodsAndAuthorizationHeader() throws Exception {
        for (String method : List.of("GET", "POST", "PATCH", "PUT", "DELETE")) {
            mockMvc.perform(options("/api/issues/7")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                            "http://localhost:5173"))
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            containsString(method)))
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            containsString("OPTIONS")));
        }
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

        for (String statusValue : List.of("PENDING", "IN_PROGRESS", "RESOLVED")) {
            mockMvc.perform(patch("/api/issues/7/status")
                            .header("Authorization", basicAuthorization())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + statusValue + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.status").value(statusValue));
        }
    }

    @Test
    void publicConfirmIncrementsReportCount() throws Exception {
        Issue issue = new Issue();
        issue.setId(7L);
        issue.setTitle("Broken streetlight");
        issue.setDescription("The light is not working.");
        issue.setStatus("PENDING");
        issue.setPriority("LOW");
        issue.setReportCount(1);
        issue.setLocation(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(79.9, 6.8)));

        when(issueRepository.findById(7L)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/issues/7/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.reportCount").value(2));

        mockMvc.perform(post("/api/issues/7/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportCount").value(3))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void issueEditingRequiresAdminAndRejectsBlankFields() throws Exception {
        Issue issue = new Issue();
        issue.setId(7L);
        issue.setTitle("Old title");
        issue.setDescription("Old description");
        issue.setStatus("PENDING");
        issue.setPriority("LOW");
        issue.setLocation(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(79.9, 6.8)));
        when(issueRepository.findById(7L)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/issues/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"description\":\"Updated description\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/issues/7")
                        .header("Authorization", basicAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"description\":\"Updated description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        mockMvc.perform(put("/api/issues/7")
                        .header("Authorization", basicAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"description\":\"Updated description\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueDeletionRequiresAdminAndRemovesItsStoredImage() throws Exception {
        Issue issue = new Issue();
        issue.setId(7L);
        issue.setImageUrl("/uploads/stored-image.png");
        when(issueRepository.findById(7L)).thenReturn(Optional.of(issue));

        mockMvc.perform(delete("/api/issues/7"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/issues/7")
                        .header("Authorization", basicAuthorization()))
                .andExpect(status().isNoContent());

        verify(issueRepository).delete(issue);
        verify(imageStorageService).delete("stored-image.png");
    }

    @Test
    void acceptsMultipartIssueWithAnAttachedImage() throws Exception {
        byte[] pngHeader = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile image = new MockMultipartFile("image", "street.png", "image/png", pngHeader);
        when(geminiService.analyzeIssue(
                eq("Pothole"),
                eq("Large hole in the road."),
                any(MultipartFile.class)))
                .thenReturn("HIGH");
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
    void acceptsMultipartIssueWithoutAnImageOrWithAnEmptyImage() throws Exception {
        when(geminiService.analyzeIssue("Pothole", "Large hole in the road.")).thenReturn("HIGH");
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue savedIssue = invocation.getArgument(0);
            savedIssue.setId(10L);
            return savedIssue;
        });

        mockMvc.perform(multipart("/api/issues")
                        .param("title", "Pothole")
                        .param("description", "Large hole in the road.")
                        .param("latitude", "6.8")
                        .param("longitude", "79.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        MockMultipartFile emptyImage =
                new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        mockMvc.perform(multipart("/api/issues")
                        .file(emptyImage)
                        .param("title", "Pothole")
                        .param("description", "Large hole in the road.")
                        .param("latitude", "6.8")
                        .param("longitude", "79.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        verifyNoInteractions(imageStorageService);
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

    @Test
    void returnsClearServerErrorWhenIssuePersistenceFails() throws Exception {
        when(geminiService.analyzeIssue("Pothole", "Large hole in the road.")).thenReturn("HIGH");
        when(issueRepository.save(any(Issue.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

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
                .andExpect(status().isInternalServerError())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        "Unable to save the issue report.",
                        result.getResponse().getErrorMessage()));
    }

    private String basicAuthorization() {
        String credentials = "test-admin:test-password";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
    }
}