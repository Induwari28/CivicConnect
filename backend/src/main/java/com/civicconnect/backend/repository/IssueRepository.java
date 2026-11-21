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
            WHERE ST_DWithin(
                location::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                50.0
            )
            AND LOWER(title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY id
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearbyDuplicate(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("title") String title);
}