package com.forenstorage.web.repository;

import com.forenstorage.web.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.forenstorage.web.model.EvidenceStatus;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Evidence e set e.status = :target where e.id = :id and e.status = :source "
            + "and e.currentPath = :path and e.archivedPath is null")
    int claimArchiving(@Param("id") Long id, @Param("path") String path,
                       @Param("source") EvidenceStatus source, @Param("target") EvidenceStatus target);
    Optional<Evidence> findByEvidenceIdentifier(String evidenceIdentifier);
    boolean existsByEvidenceIdentifier(String evidenceIdentifier);
}
