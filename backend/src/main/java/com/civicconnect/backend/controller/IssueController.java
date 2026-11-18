package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.IssueRequest;
import com.civicconnect.backend.model.Issue;
import com.civicconnect.backend.repository.IssueRepository;
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
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public IssueController(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    // Modern Java 21 Record to act as our DTO, avoiding complex Map generics entirely
    public record IssueResponse(Long id, String title, String description, String status, double latitude, double longitude) {}

    @GetMapping
    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll().stream().map(issue -> new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getLocation().getY(), // latitude
                issue.getLocation().getX()  // longitude
        )).collect(Collectors.toList());
    }

    @PostMapping
    public IssueResponse createIssue(@RequestBody IssueRequest request) {
        Issue issue = new Issue();
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());

        Point location = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
        issue.setLocation(location);

        Issue savedIssue = issueRepository.save(issue);
        
        return new IssueResponse(
                savedIssue.getId(),
                savedIssue.getTitle(),
                savedIssue.getDescription(),
                savedIssue.getStatus(),
                savedIssue.getLocation().getY(),
                savedIssue.getLocation().getX()
        );
    }
}