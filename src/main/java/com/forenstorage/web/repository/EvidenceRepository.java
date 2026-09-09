package com.forenstorage.web.repository;

import com.forenstorage.web.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    Optional<Evidence> findByEvidenceIdentifier(String evidenceIdentifier);
    boolean existsByEvidenceIdentifier(String evidenceIdentifier);
}
