package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.IssueRequest;
import com.civicconnect.backend.model.Issue;
import com.civicconnect.backend.repository.IssueRepository;
import com.civicconnect.backend.service.GeminiService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "http://localhost:5173")
public class IssueController {

    private final IssueRepository issueRepository;
    private final GeminiService geminiService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public IssueController(IssueRepository issueRepository, GeminiService geminiService) {
        this.issueRepository = issueRepository;
        this.geminiService = geminiService;
    }

    // Updated DTO Record to include priority
    public record IssueResponse(Long id, String title, String description, String status, String priority, double latitude, double longitude) {}

    @GetMapping
    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll().stream().map(issue -> new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getLocation().getY(),
                issue.getLocation().getX()
        )).collect(Collectors.toList());
    }

    @PostMapping
    public IssueResponse createIssue(@RequestBody IssueRequest request) {
        Issue issue = new Issue();
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());

        // 1. Ask Gemini to analyze the issue and assign a priority
        String aiPriority = geminiService.analyzeIssue(request.getTitle(), request.getDescription());
        issue.setPriority(aiPriority);

        // 2. Convert standard GPS coordinates into PostGIS spatial point
        Point location = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
        issue.setLocation(location);

        // 3. Save to database
        Issue savedIssue = issueRepository.save(issue);
        
        return new IssueResponse(
                savedIssue.getId(),
                savedIssue.getTitle(),
                savedIssue.getDescription(),
                savedIssue.getStatus(),
                savedIssue.getPriority(),
                savedIssue.getLocation().getY(),
                savedIssue.getLocation().getX()
        );
    }
}