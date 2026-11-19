package com.civicconnect.backend.controller;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
            String imageUrl,
            int reportCount) {}

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
        return processAndSaveIssue(
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
        return processAndSaveIssue(title, description, latitude, longitude, image);
    }

    @PostMapping("/{id}/confirm")
public IssueResponse confirmIssue(@PathVariable Long id) {
    Issue issue = issueRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found."));

    issue.incrementReportCount();

    if (issue.getReportCount() >= 3 && !"HIGH".equalsIgnoreCase(issue.getPriority())) {
        issue.setPriority("HIGH");
    }

    return toResponse(issueRepository.save(issue));
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

    private IssueResponse processAndSaveIssue(
            String title,
            String description,
            double latitude,
            double longitude,
            MultipartFile image) {

        // 1. Check for spatial duplicate within 50 meters
        Optional<Issue> existingDuplicate = issueRepository.findNearbyDuplicate(latitude, longitude, title);

        if (existingDuplicate.isPresent()) {
            Issue duplicate = existingDuplicate.get();
            duplicate.incrementReportCount();
            
            // Smart Crowdsourcing Boost: 3+ reports automatically escalates priority to HIGH
            if (duplicate.getReportCount() >= 3 && !"HIGH".equalsIgnoreCase(duplicate.getPriority())) {
                duplicate.setPriority("HIGH");
            }
            
            return toResponse(issueRepository.save(duplicate));
        }

        // 2. Otherwise, create a brand-new issue
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setReportCount(1);
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        issue.setLocation(location);
        issue.setStatus(IssueStatus.PENDING.name());

        String storedImageFilename = null;
        try {
            if (image != null && !image.isEmpty()) {
                storedImageFilename = imageStorageService.store(image);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the uploaded image.",
                    exception);
        }
        if (storedImageFilename != null) {
            issue.setImageUrl("/uploads/" + storedImageFilename);
        }

        try {
            issue.setPriority(geminiService.analyzeIssue(title, description));
            return toResponse(issueRepository.save(issue));
        } catch (RuntimeException exception) {
            cleanupStoredImage(storedImageFilename, exception);
            throw exception;
        }
    }

    private IssueResponse toResponse(Issue issue) {
        Point location = issue.getLocation();
        if (location == null) {
            throw new IllegalStateException("Issue location is missing.");
        }
        return new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getPriority(),
                location.getY(),
                location.getX(),
                issue.getImageUrl(),
                issue.getReportCount());
    }

    private void cleanupStoredImage(String filename, RuntimeException originalException) {
        if (filename == null) return;
        try {
            imageStorageService.delete(filename);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}