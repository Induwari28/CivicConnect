package com.civicconnect.backend.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.time.LocalDateTime;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String status = "PENDING"; // PENDING, TRIAGED, RESOLVED

    // This is the PostGIS spatial column (SRID 4326 is the standard GPS coordinate system)
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Standard empty constructor required by JPA
    public Issue() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}