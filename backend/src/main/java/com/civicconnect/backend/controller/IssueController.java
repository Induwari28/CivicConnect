package com.civicconnect.backend.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "http://localhost:5173")
public class IssueController {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssueController.class);

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

    public record UpdateIssueRequest(String title, String description) {}

    @GetMapping
    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll().stream()
                .filter(this::hasValidLocation)
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/admin")
    public List<IssueResponse> getAdminIssues() {
        return getAllIssues();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public IssueResponse createIssue(@RequestBody IssueRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue details are required.");
        }
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteIssue(@PathVariable Long id) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found."));
        issueRepository.delete(issue);

        String imageFilename = getStoredImageFilename(issue.getImageUrl());
        if (imageFilename == null) {
            return;
        }
        try {
            imageStorageService.delete(imageFilename);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to delete the issue photo.",
                    exception);
        }
    }

    @PutMapping("/{id}")
    public IssueResponse updateIssue(@PathVariable Long id, @RequestBody UpdateIssueRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()
                || request.description() == null || request.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and description are required.");
        }

        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found."));

        issue.setTitle(request.title().trim());
        issue.setDescription(request.description().trim());

        return toResponse(issueRepository.save(issue));
    }

    @Transactional
    private IssueResponse processAndSaveIssue(
            String title,
            String description,
            double latitude,
            double longitude,
            MultipartFile image) {

        validateIssueData(title, description, latitude, longitude);

        // 1. Check for an active spatial cluster within 50 meters
        Optional<Issue> existingCluster;
        try {
            existingCluster = issueRepository.findNearbyDuplicate(latitude, longitude);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to query for nearby clustered issues within 50m.", exception);
            throw persistenceFailure("Unable to check for nearby issue reports.", exception);
        }

        if (existingCluster.isPresent()) {
            Issue clusteredIssue = existingCluster.get();
            clusteredIssue.incrementReportCount();

            if (description != null && !description.isBlank()) {
                String currentDesc = clusteredIssue.getDescription() != null ? clusteredIssue.getDescription() : "";
                if (!currentDesc.contains(description.trim())) {
                    clusteredIssue.setDescription(
                            currentDesc.isBlank()
                                    ? description.trim()
                                    : currentDesc + " | Additional report: " + description.trim());
                }
            }

            // Smart Crowdsourcing Boost: 3+ reports automatically escalates priority to HIGH
            if (clusteredIssue.getReportCount() >= 3 && !"HIGH".equalsIgnoreCase(clusteredIssue.getPriority())) {
                clusteredIssue.setPriority("HIGH");
            }

            clusteredIssue.ensureRequiredFields();
            return toResponse(saveIssue(clusteredIssue, "updated clustered issue"));
        }

        // 2. Otherwise, create a brand-new issue node
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setReportCount(1);
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        location.setSRID(4326);
        issue.setLocation(location);
        issue.setStatus(IssueStatus.PENDING.name());
        issue.setCreatedAt(LocalDateTime.now());

        String storedImageFilename = null;
        try {
            if (image != null && !image.isEmpty()) {
                storedImageFilename = imageStorageService.store(image);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to store the uploaded image for an issue report.", exception);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the uploaded image.",
                    exception);
        }
        if (storedImageFilename != null) {
            issue.setImageUrl("/uploads/" + storedImageFilename);
        }

        try {
            issue.setPriority(normalizePriority(geminiService.analyzeIssue(title, description)));
            issue.ensureRequiredFields();
            return toResponse(saveIssue(issue, "new issue"));
        } catch (RuntimeException exception) {
            cleanupStoredImage(storedImageFilename, exception);
            throw exception;
        }
    }

    private void validateIssueData(String title, String description, double latitude, double longitude) {
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and description are required.");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required.");
        }
    }

    private IssueResponse toResponse(Issue issue) {
        Point location = issue.getLocation();
        if (!isValidLocation(location)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Issue has missing or invalid location coordinates.");
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

    private boolean hasValidLocation(Issue issue) {
        if (isValidLocation(issue.getLocation())) {
            return true;
        }
        LOGGER.warn("Skipping issue {} from the public feed because its location is missing or invalid.",
                issue.getId());
        return false;
    }

    private boolean isValidLocation(Point location) {
        if (location == null || location.isEmpty() || location.getSRID() != 4326) {
            return false;
        }
        double longitude = location.getX();
        double latitude = location.getY();
        return Double.isFinite(longitude) && longitude >= -180 && longitude <= 180
                && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90;
    }

    private String normalizePriority(String priority) {
        if (priority == null) {
            return "LOW";
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "LOW";
        };
    }

    private Issue saveIssue(Issue issue, String operation) {
        try {
            return issueRepository.save(issue);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to persist {}.", operation, exception);
            throw persistenceFailure("Unable to save the issue report.", exception);
        }
    }

    private ResponseStatusException persistenceFailure(String message, RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    private void cleanupStoredImage(String filename, RuntimeException originalException) {
        if (filename == null) return;
        try {
            imageStorageService.delete(filename);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private String getStoredImageFilename(String imageUrl) {
        String uploadPrefix = "/uploads/";
        if (imageUrl == null || !imageUrl.startsWith(uploadPrefix)) {
            return null;
        }
        return imageUrl.substring(uploadPrefix.length());
    }
}