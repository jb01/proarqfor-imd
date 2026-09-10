package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EvidenceRegistrationServiceTest {
    private static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @TempDir
    static Path directory;
    @Autowired EvidenceRegistrationService service;
    @Autowired EvidenceRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve("registration.db"));
        properties.add("forenstorage.storage.fast", () -> directory.resolve("fast").toString());
    }

    private Path file(String name) throws IOException {
        return Files.writeString(Files.createDirectories(directory.resolve("fast")).resolve(name), "abc");
    }

    @Test
    void correctHashPersistsAnalysisAndBothHashes() throws IOException {
        Path path = file("correct.dd");
        String informed = ABC_HASH.toUpperCase(Locale.ROOT);
        Evidence saved = service.register("correct/2026", path, informed);
        Evidence loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("correct/2026", loaded.getEvidenceIdentifier());
        assertEquals(path.toRealPath().toString(), loaded.getCurrentPath());
        assertEquals(EvidenceStatus.EM_ANALISE, loaded.getStatus());
        assertEquals(informed, loaded.getInformedHash());
        assertEquals(ABC_HASH, loaded.getCalculatedHash());
        assertNotNull(loaded.getCreatedAt());
        assertEquals("abc", Files.readString(path));
    }

    @Test
    void divergentHashPersistsAndBlocksEveryTransitionIncludingArchiving() throws IOException {
        Evidence saved = service.register("divergent/2026", file("divergent.dd"), "0".repeat(64));
        Evidence loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(EvidenceStatus.HASH_DIVERGENTE, loaded.getStatus());
        assertEquals("0".repeat(64), loaded.getInformedHash());
        assertEquals(ABC_HASH, loaded.getCalculatedHash());
        for (EvidenceStatus target : EvidenceStatus.values()) {
            assertThrows(IllegalStateException.class, () -> loaded.transitionTo(target));
        }
        assertEquals(EvidenceStatus.HASH_DIVERGENTE, loaded.getStatus());
    }

    @Test
    void duplicateDoesNotOverwriteExistingEvidence() throws IOException {
        Path first = file("first.dd");
        Evidence saved = service.register("duplicate/2026", first, ABC_HASH);
        long count = repository.count();
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.register("duplicate/2026", file("second.dd"), "0".repeat(64)));
        assertTrue(error.getMessage().contains("já cadastrado"));
        assertEquals(count, repository.count());
        assertEquals(first.toRealPath().toString(), repository.findById(saved.getId()).orElseThrow().getCurrentPath());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg"})
    void rejectsMalformedHashesWithoutSaving(String hash) throws IOException {
        Path path = file("invalid-hash.dd");
        long count = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.register("bad-hash/2026", path, hash));
        assertEquals(count, repository.count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingIdentifier(String identifier) throws IOException {
        Path path = file("no-id.dd");
        long count = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.register(identifier, path, ABC_HASH));
        assertEquals(count, repository.count());
    }

    @Test
    void rejectsMissingFileWithoutSaving() throws IOException {
        Files.createDirectories(directory.resolve("fast"));
        long count = repository.count();
        IOException error = assertThrows(IOException.class,
                () -> service.register("missing/2026", directory.resolve("fast/missing.dd"), ABC_HASH));
        assertTrue(error.getMessage().contains("Arquivo não encontrado"));
        assertEquals(count, repository.count());
    }

    @Test
    void rejectsOutsidePathsWrongExtensionAndDirectories() throws IOException {
        Path outside = Files.writeString(directory.resolve("outside.dd"), "abc");
        Path wrongExtension = file("wrong.txt");
        Path folder = Files.createDirectories(directory.resolve("fast/folder.dd"));
        long count = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.register("outside", outside, ABC_HASH));
        assertThrows(IllegalArgumentException.class, () -> service.register("escape", directory.resolve("fast/../outside.dd"), ABC_HASH));
        assertThrows(IllegalArgumentException.class, () -> service.register("extension", wrongExtension, ABC_HASH));
        assertThrows(IOException.class, () -> service.register("folder", folder, ABC_HASH));
        assertThrows(IllegalArgumentException.class, () -> service.register("null", null, ABC_HASH));
        assertEquals(count, repository.count());
    }

    @Test
    void rejectsFileAndParentSymlinks() throws IOException {
        Path original = file("original.dd");
        Path link = Files.createSymbolicLink(directory.resolve("fast/link.dd"), original);
        Path parentLink = Files.createSymbolicLink(directory.resolve("fast/linked-directory"), directory.resolve("fast"));
        long count = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.register("link", link, ABC_HASH));
        assertThrows(IllegalArgumentException.class, () -> service.register("parent-link", parentLink.resolve("original.dd"), ABC_HASH));
        assertEquals(count, repository.count());
    }

    @Test
    void interruptedHashReadDoesNotCreatePartialRecord() throws IOException {
        Path path = file("interrupted.dd");
        HashService failingHash = new HashService() {
            @Override public String calculateSha256(Path ignored) throws IOException {
                throw new IOException("Leitura interrompida");
            }
        };
        var useCase = new EvidenceRegistrationService(repository, failingHash, directory.resolve("fast").toString());
        long count = repository.count();
        assertThrows(IOException.class, () -> useCase.register("interrupted", path, ABC_HASH));
        assertEquals(count, repository.count());
    }
    @Test
    void translatesDuplicateDetectedByDatabaseAfterPrecheck() throws IOException {
        Path path = file("race.dd");
        var racingRepository = org.mockito.Mockito.mock(EvidenceRepository.class);
        var failure = new org.springframework.orm.jpa.JpaSystemException(new RuntimeException(
                "[SQLITE_CONSTRAINT_UNIQUE] UNIQUE constraint failed: evidence.evidence_identifier"));
        org.mockito.Mockito.when(racingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Evidence.class)))
                .thenThrow(failure);
        var useCase = new EvidenceRegistrationService(racingRepository, new HashService(), directory.resolve("fast").toString());
        var error = assertThrows(IllegalArgumentException.class, () -> useCase.register("race", path, ABC_HASH));
        assertTrue(error.getMessage().contains("já cadastrado"));
        assertSame(failure, error.getCause());
    }

    @Test
    void doesNotMislabelOtherDatabaseFailuresAsDuplicates() throws IOException {
        Path path = file("db-failure.dd");
        var unavailableRepository = org.mockito.Mockito.mock(EvidenceRepository.class);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("Banco indisponível");
        org.mockito.Mockito.when(unavailableRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Evidence.class)))
                .thenThrow(failure);
        var useCase = new EvidenceRegistrationService(unavailableRepository, new HashService(), directory.resolve("fast").toString());
        assertSame(failure, assertThrows(org.springframework.dao.DataAccessException.class,
                () -> useCase.register("db-failure", path, ABC_HASH)));
    }
}
