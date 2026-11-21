package com.civicconnect.backend.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;
    private String priority = "LOW";
    private String status = "PENDING";
    private String imageUrl;

    @Column(name = "report_count")
    private Integer reportCount = 1;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void ensureRequiredFields() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (reportCount == null || reportCount < 1) {
            reportCount = 1;
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (priority == null) {
            priority = "LOW";
        } else {
            String normalizedPriority = priority.trim().toUpperCase(Locale.ROOT);
            priority = switch (normalizedPriority) {
                case "HIGH", "MEDIUM", "LOW" -> normalizedPriority;
                default -> "LOW";
            };
        }
    }

    public boolean hasValidLocation() {
        if (location == null || location.isEmpty()) {
            return false;
        }
        if (location.getSRID() != 4326) {
            return false;
        }
        double longitude = location.getX();
        double latitude = location.getY();
        return Double.isFinite(longitude) && longitude >= -180 && longitude <= 180
                && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getReportCount() {
        return reportCount != null ? reportCount : 1;
    }

    public void setReportCount(Integer reportCount) {
        this.reportCount = reportCount;
    }

    public void incrementReportCount() {
        this.reportCount = (this.reportCount != null ? this.reportCount : 1) + 1;
    }
}