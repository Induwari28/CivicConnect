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
              AND CASE
                  WHEN location IS NOT NULL
                       AND ST_SRID(location) = 4326
                       AND NOT ST_IsEmpty(location)
                       AND ST_X(location) BETWEEN -180 AND 180
                       AND ST_Y(location) BETWEEN -90 AND 90
                  THEN ST_DWithin(
                      location::geography,
                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                      50.0
                  )
                  ELSE FALSE
              END
            ORDER BY report_count DESC NULLS LAST, id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearbyDuplicate(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude);

    @Query(value = """
            SELECT *
            FROM issues
            WHERE status <> 'RESOLVED'
              AND (
                  (:category IS NULL
                   AND SUBSTRING(title FROM '^\\[([^\\]]+)\\]') IS NULL)
                  OR
                  (:category IS NOT NULL
                   AND LOWER(TRIM(SUBSTRING(title FROM '^\\[([^\\]]+)\\]'))) = LOWER(TRIM(:category)))
              )
              AND CASE
                  WHEN location IS NOT NULL
                       AND ST_SRID(location) = 4326
                       AND NOT ST_IsEmpty(location)
                       AND ST_X(location) BETWEEN -180 AND 180
                       AND ST_Y(location) BETWEEN -90 AND 90
                  THEN ST_DWithin(
                      location::geography,
                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                      50.0
                  )
                  ELSE FALSE
              END
            ORDER BY report_count DESC NULLS LAST, id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearbyDuplicateByCategory(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("category") String category);
}