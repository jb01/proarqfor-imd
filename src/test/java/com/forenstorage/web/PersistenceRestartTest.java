package com.forenstorage.web;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import com.forenstorage.web.service.EvidenceRegistrationService;
import com.forenstorage.web.service.ArchivingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceRestartTest {
    @TempDir Path root;

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(WebApplication.class).web(WebApplicationType.NONE)
                .run("--forenstorage.data-root=" + root, "--spring.main.banner-mode=off");
    }

    @Test
    void newApplicationContextReloadsRecordsMetadataAndArtifacts() throws Exception {
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        Path fast = Files.createDirectories(root.resolve("storage/fast"));
        Path dd = Files.writeString(fast.resolve("sample.dd"), "abc");
        Evidence archived;
        Long pendingId;
        byte[] ciphertext;
        try (var first = start()) {
            var registration = first.getBean(EvidenceRegistrationService.class);
            Evidence initial = registration.register("restart-archive", dd, hash);
            first.getBean(ArchivingService.class).archive(initial.getId());
            archived = first.getBean(EvidenceRepository.class).findById(initial.getId()).orElseThrow();
            ciphertext = Files.readAllBytes(Path.of(archived.getCurrentPath()));
            pendingId = registration.register("restart-pending", Files.writeString(fast.resolve("pending.dd"), "abc"), hash).getId();
            assertTrue(Files.isRegularFile(root.resolve("data/arqfor.db")));
        }
        // The first EntityManagerFactory and connection pool are closed before a fresh application starts.
        try (var second = start()) {
            var repository = second.getBean(EvidenceRepository.class);
            Evidence reloaded = repository.findById(archived.getId()).orElseThrow();
            assertEquals(EvidenceStatus.ARQUIVADO, reloaded.getStatus());
            assertEquals(archived.getCurrentPath(), reloaded.getCurrentPath());
            assertEquals(archived.getArchivedPath(), reloaded.getArchivedPath());
            assertEquals(archived.getCreatedAt(), reloaded.getCreatedAt());
            assertEquals(hash, reloaded.getInformedHash());
            assertEquals(hash, reloaded.getCalculatedHash());
            assertTrue(archived.getEncryptionKey().equals(reloaded.getEncryptionKey()));
            assertTrue(archived.getEncryptionPassword().equals(reloaded.getEncryptionPassword()));
            assertTrue(archived.getEncryptionIv().equals(reloaded.getEncryptionIv()));
            assertTrue(archived.getEncryptionSalt().equals(reloaded.getEncryptionSalt()));
            assertEquals(archived.getEncryptionIterations(), reloaded.getEncryptionIterations());
            assertEquals(archived.getEncryptionFormatVersion(), reloaded.getEncryptionFormatVersion());
            assertArrayEquals(ciphertext, Files.readAllBytes(Path.of(reloaded.getCurrentPath())));
            Evidence pending = repository.findById(pendingId).orElseThrow();
            assertEquals(EvidenceStatus.EM_ANALISE, pending.getStatus());
            assertNull(pending.getEncryptionKey());
            assertNull(pending.getEncryptionIv());
            assertEquals("abc", Files.readString(Path.of(pending.getCurrentPath())));
            var duplicate = assertThrows(DataAccessException.class, () -> repository.saveAndFlush(
                    new Evidence("restart-pending", pending.getCurrentPath(), hash, hash, EvidenceStatus.EM_ANALISE)));
            assertTrue(duplicate.getMostSpecificCause().getMessage().contains("SQLITE_CONSTRAINT_UNIQUE"));
            assertEquals(2, repository.count());
        }
    }

    @Test
    void invalidDataDirectoryFailsStartupWithoutReplacingFile() throws Exception {
        Path obstruction = Files.writeString(root.resolve("data"), "preserve");
        assertThrows(Exception.class, () -> { try (var ignored = start()) { } });
        assertEquals("preserve", Files.readString(obstruction));
    }
}
