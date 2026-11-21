package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query(value = """
            SELECT *
            FROM issues
            WHERE status <> 'RESOLVED'
              AND ST_DWithin(
                  location::geography,
                  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                  50.0
              )
            ORDER BY report_count DESC NULLS LAST, id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearbyDuplicate(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude);
}