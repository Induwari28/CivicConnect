package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, Long> {
}