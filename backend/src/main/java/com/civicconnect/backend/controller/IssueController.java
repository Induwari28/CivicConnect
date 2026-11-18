package com.civicconnect.backend.controller;

import java.io.IOException;
import java.util.List;

import com.civicconnect.backend.dto.IssueRequest;
import com.civicconnect.backend.model.Issue;
import com.civicconnect.backend.model.IssueStatus;
import com.civicconnect.backend.repository.IssueRepository;
import com.civicconnect.backend.service.GeminiService;
import com.civicconnect.backend.service.IssueImageStorageService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "http://localhost:5173")
public class IssueController {

    private final IssueRepository issueRepository;
    private final GeminiService geminiService;
    private final IssueImageStorageService imageStorageService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public IssueController(
            IssueRepository issueRepository,
            GeminiService geminiService,
            IssueImageStorageService imageStorageService) {
        this.issueRepository = issueRepository;
        this.geminiService = geminiService;
        this.imageStorageService = imageStorageService;
    }

    public record IssueResponse(
            Long id,
            String title,
            String description,
            String status,
            String priority,
            double latitude,
            double longitude,
            String imageUrl) {}

    public record StatusUpdateRequest(IssueStatus status) {}

    @GetMapping
    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/admin")
    public List<IssueResponse> getAdminIssues() {
        return getAllIssues();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public IssueResponse createIssue(@RequestBody IssueRequest request) {
        return createIssue(
                request.getTitle(),
                request.getDescription(),
                request.getLatitude(),
                request.getLongitude(),
                null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IssueResponse createIssueWithImage(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return createIssue(title, description, latitude, longitude, image);
    }

    @PatchMapping("/{id}/status")
    public IssueResponse updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required.");
        }

        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found."));
        issue.setStatus(request.status().name());
        return toResponse(issueRepository.save(issue));
    }

    private IssueResponse createIssue(
            String title,
            String description,
            double latitude,
            double longitude,
            MultipartFile image) {
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setDescription(description);
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        issue.setLocation(location);

        String imageFilename;
        try {
            imageFilename = imageStorageService.store(image);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the uploaded image.",
                    exception);
        }
        issue.setImageFilename(imageFilename);

        try {
            issue.setPriority(geminiService.analyzeIssue(title, description));
            return toResponse(issueRepository.save(issue));
        } catch (RuntimeException exception) {
            cleanupStoredImage(imageFilename, exception);
            throw exception;
        }
    }

    private IssueResponse toResponse(Issue issue) {
        String imageUrl = issue.getImageFilename() == null
                ? null
                : "/uploads/" + issue.getImageFilename();
        return new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getLocation().getY(),
                issue.getLocation().getX(),
                imageUrl);
    }

    private void cleanupStoredImage(String imageFilename, RuntimeException originalException) {
        if (imageFilename == null) return;
        try {
            imageStorageService.delete(imageFilename);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
