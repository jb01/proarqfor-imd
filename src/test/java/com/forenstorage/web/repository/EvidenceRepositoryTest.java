package com.forenstorage.web.repository;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.dao.DataAccessException;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EvidenceRepositoryTest {
    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve("evidence.db"));
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    EvidenceRepository repository;

    @Test
    void persistsAndReloadsFieldsAndCommittedDataAcrossConnections() throws Exception {
        Evidence evidence = new Evidence("persist/2026", "storage/fast/test.dd",
                "a".repeat(64), "b".repeat(64), EvidenceStatus.HASH_DIVERGENTE);
        Evidence saved = repository.saveAndFlush(evidence);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        Evidence loaded = repository.findById(saved.getId()).orElseThrow();
        assertNotSame(saved, loaded);
        assertEquals(evidence.getEvidenceIdentifier(), loaded.getEvidenceIdentifier());
        assertEquals(evidence.getCurrentPath(), loaded.getCurrentPath());
        assertEquals(evidence.getInformedHash(), loaded.getInformedHash());
        assertEquals(evidence.getCalculatedHash(), loaded.getCalculatedHash());
        assertEquals(EvidenceStatus.HASH_DIVERGENTE, loaded.getStatus());
        assertNull(loaded.getArchivedPath());
        assertNull(loaded.getEncryptionIv());
        assertNull(loaded.getErrorMessage());
        assertTrue(repository.existsByEvidenceIdentifier("persist/2026"));
        assertEquals(saved.getId(), repository.findByEvidenceIdentifier("persist/2026").orElseThrow().getId());
        assertThrows(IllegalStateException.class, () -> loaded.transitionTo(EvidenceStatus.EM_ANALISE));
        // A fresh physical connection sees committed SQLite data, not the JPA cache.
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("evidence.db"));
             var statement = connection.prepareStatement("select status from evidence where id = ?")) {
            assertEquals("SQLite", connection.getMetaData().getDatabaseProductName());
            statement.setLong(1, saved.getId());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("HASH_DIVERGENTE", rows.getString(1));
            }
        }
    }

    @Test
    void persistsMetadataAndStatusChangesWithoutResettingCreationDate() {
        Evidence e = repository.saveAndFlush(new Evidence("update/2026", "storage/fast/test.dd",
                "a".repeat(64), "a".repeat(64), EvidenceStatus.EM_ANALISE));
        Evidence loaded = repository.findById(e.getId()).orElseThrow();
        var originalDate = loaded.getCreatedAt();
        loaded.setArchivedPath("storage/cold/archive/test.zip.enc");
        loaded.setEncryptionIv("didactic-iv");
        loaded.setCurrentPath("storage/cold/work/test.dd");
        loaded.markError("Erro simulado");
        repository.saveAndFlush(loaded);
        Evidence updated = repository.findById(e.getId()).orElseThrow();
        assertEquals(originalDate, updated.getCreatedAt());
        assertEquals("storage/cold/archive/test.zip.enc", updated.getArchivedPath());
        assertEquals("didactic-iv", updated.getEncryptionIv());
        assertEquals("storage/cold/work/test.dd", updated.getCurrentPath());
        assertEquals("Erro simulado", updated.getErrorMessage());
        assertEquals(EvidenceStatus.ERRO, updated.getStatus());
        assertThrows(IllegalStateException.class, () -> updated.transitionTo(EvidenceStatus.EM_ANALISE));
    }

    @Test
    void enforcesUniqueIdentifierInSQLite() {
        repository.saveAndFlush(new Evidence("unique/2026", "one.dd", "a", "a", EvidenceStatus.EM_ANALISE));
        var failure = assertThrows(DataAccessException.class, () -> repository.saveAndFlush(
                new Evidence("unique/2026", "two.dd", "b", "b", EvidenceStatus.EM_ANALISE)));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("SQLITE_CONSTRAINT_UNIQUE"));
        assertEquals("one.dd", repository.findByEvidenceIdentifier("unique/2026").orElseThrow().getCurrentPath());
    }
}
