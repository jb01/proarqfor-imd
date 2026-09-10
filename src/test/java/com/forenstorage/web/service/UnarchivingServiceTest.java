package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnarchivingServiceTest {
    @TempDir static Path databaseDirectory;
    @TempDir Path directory;
    @Autowired EvidenceRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    private UnarchivingService service;
    private Path fast;
    private Path archive;
    private Path source;
    private Path destination;
    private Long id;
    private Evidence archived;
    private byte[] content;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", UnarchivingServiceTest::databaseUrl);
    }

    @BeforeEach
    void setUp() throws IOException {
        fast = directory.resolve("fast");
        archive = Files.createDirectory(directory.resolve("archive"));
        Evidence evidence = new Evidence(UUID.randomUUID().toString(), "synthetic.dd",
                "a".repeat(64), "a".repeat(64), EvidenceStatus.EM_ANALISE);
        id = repository.saveAndFlush(evidence).getId();
        content = new byte[20_000];
        new Random(42).nextBytes(content);
        source = Files.write(Files.createDirectory(archive.resolve(id.toString())).resolve("synthetic.dd.zip.enc"), content);
        destination = fast.resolve(id.toString()).resolve(source.getFileName());
        evidence.transitionTo(EvidenceStatus.ARQUIVANDO);
        // Synthetic metadata only: no real key or password in this fixture.
        evidence.recordEncryption(source.toString(), "test-iv", "test-password", "test-key", "test-salt", 600000, 1);
        evidence.transitionTo(EvidenceStatus.ARQUIVADO);
        repository.saveAndFlush(evidence);
        archived = repository.findById(id).orElseThrow();
        service = spy(new UnarchivingService(repository, transactionManager, fast.toString(), archive.toString()));
    }

    @Test
    void movesCiphertextOnlyAfterCommittedIntermediateStateAndPreservesMetadata() throws Exception {
        doAnswer(invocation -> {
            assertEquals("DESARQUIVANDO", sqlValue("status"));
            assertEquals(source.toString(), sqlValue("current_path"));
            return invocation.callRealMethod();
        }).when(service).move(source, destination);

        Evidence result = service.unarchive(id);

        assertEquals(EvidenceStatus.EM_ANALISE, result.getStatus());
        assertEquals("EM_ANALISE", sqlValue("status"));
        assertEquals(destination.toString(), sqlValue("current_path"));
        assertFalse(Files.exists(source));
        assertArrayEquals(content, Files.readAllBytes(destination));
        assertTrue(destination.toString().endsWith(".zip.enc"));
        assertMetadataPreserved(repository.findById(id).orElseThrow());
        verify(service, times(1)).move(source, destination);
        assertThrows(IllegalStateException.class, () -> service.unarchive(id));
    }

    @Test
    void actualArchiveCanReturnWithoutRestorationAndCannotBeArchivedAgain() throws Exception {
        Path work = directory.resolve("work");
        Files.createDirectories(fast);
        Path dd = Files.write(fast.resolve("original.dd"), content);
        Evidence original = repository.saveAndFlush(new Evidence(UUID.randomUUID().toString(), dd.toString(),
                "a".repeat(64), "a".repeat(64), EvidenceStatus.EM_ANALISE));
        ArchivingService archiving = new ArchivingService(repository,
                new StorageCopyService(fast.toString(), work.toString(), archive.toString()),
                new ZipService(work.toString()),
                new CryptoService(repository, transactionManager, work.toString(), archive.toString()), transactionManager);
        Evidence encrypted = archiving.archive(original.getId());
        byte[] ciphertext = Files.readAllBytes(Path.of(encrypted.getCurrentPath()));
        Evidence returned = service.unarchive(original.getId());
        assertEquals(EvidenceStatus.EM_ANALISE, returned.getStatus());
        assertArrayEquals(ciphertext, Files.readAllBytes(Path.of(returned.getCurrentPath())));
        assertFalse(Files.exists(Path.of(encrypted.getCurrentPath())));
        assertFalse(Files.exists(dd));
        assertTrue(encrypted.getEncryptionKey().equals(returned.getEncryptionKey()));
        assertTrue(encrypted.getEncryptionIv().equals(returned.getEncryptionIv()));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> archiving.archive(original.getId()));
        assertTrue(error.getMessage().contains("cadastre novamente um .dd"));
        assertEquals(EvidenceStatus.EM_ANALISE, repository.findById(original.getId()).orElseThrow().getStatus());
        try (var files = Files.walk(work)) {
            Path retained = files.filter(p -> p.toString().endsWith(".dd")).findFirst().orElseThrow();
            assertArrayEquals(content, Files.readAllBytes(retained));
        }
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = "ARQUIVADO", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAllOtherStatesWithoutMovement(EvidenceStatus status) throws Exception {
        Evidence blocked = new Evidence(UUID.randomUUID().toString(), source.toString(), "a", "a",
                status == EvidenceStatus.HASH_DIVERGENTE ? status : EvidenceStatus.EM_ANALISE);
        if (status == EvidenceStatus.ERRO) blocked.markError("synthetic");
        if (status == EvidenceStatus.ARQUIVANDO || status == EvidenceStatus.DESARQUIVANDO) {
            blocked.transitionTo(EvidenceStatus.ARQUIVANDO);
            if (status == EvidenceStatus.DESARQUIVANDO) {
                blocked.transitionTo(EvidenceStatus.ARQUIVADO);
                blocked.transitionTo(status);
            }
        }
        Long blockedId = repository.saveAndFlush(blocked).getId();
        assertThrows(IllegalStateException.class, () -> service.unarchive(blockedId));
        assertEquals(status, repository.findById(blockedId).orElseThrow().getStatus());
        verify(service, never()).move(any(), any());
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void missingSourceBecomesError() {
        Evidence evidence = repository.findById(id).orElseThrow();
        String missing = source.resolveSibling("missing.zip.enc").toString();
        evidence.setCurrentPath(missing);
        evidence.setArchivedPath(missing);
        repository.saveAndFlush(evidence);
        assertThrows(IOException.class, () -> service.unarchive(id));
        assertEquals(EvidenceStatus.ERRO, repository.findById(id).orElseThrow().getStatus());
        assertFalse(Files.exists(destination));
    }

    @ParameterizedTest
    @ValueSource(strings = {"outside", "sibling", "extension", "directory", "source-link", "parent-link", "destination-link"})
    void rejectsUnsafePathsWithoutMovingFiles(String kind) throws Exception {
        Path invalid = switch (kind) {
            case "outside" -> Files.write(directory.resolve("outside.zip.enc"), content);
            case "sibling" -> Files.write(Files.createDirectory(archive.resolve("other")).resolve("other.zip.enc"), content);
            case "extension" -> Files.write(source.resolveSibling("original.dd"), content);
            case "directory" -> Files.createDirectory(source.resolveSibling("folder.zip.enc"));
            case "source-link" -> Files.createSymbolicLink(source.resolveSibling("link.zip.enc"), source);
            case "parent-link" -> {
                Files.createDirectories(fast);
                Files.createSymbolicLink(fast.resolve(id.toString()), archive.resolve(id.toString()));
                yield source;
            }
            case "destination-link" -> {
                Files.createDirectories(destination.getParent());
                Files.createSymbolicLink(destination, source);
                yield source;
            }
            default -> throw new AssertionError(kind);
        };
        Evidence evidence = repository.findById(id).orElseThrow();
        evidence.setCurrentPath(invalid.toString());
        evidence.setArchivedPath(invalid.toString());
        repository.saveAndFlush(evidence);
        assertThrows(IOException.class, () -> service.unarchive(id));
        assertEquals(EvidenceStatus.ERRO, repository.findById(id).orElseThrow().getStatus());
        verify(service, never()).move(any(), any());
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void collisionPreservesBothFilesAndMarksErrorWithoutRetry() throws Exception {
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "foreign file");
        assertThrows(IOException.class, () -> service.unarchive(id));
        assertEquals("foreign file", Files.readString(destination));
        assertArrayEquals(content, Files.readAllBytes(source));
        assertError(source);
        verify(service, times(1)).move(source, destination);
    }

    @Test
    void movementFailureIsTerminalAndSanitizedWithoutRetry() throws Exception {
        doThrow(new IOException("synthetic-secret")).when(service).move(any(), any());
        IOException error = assertThrows(IOException.class, () -> service.unarchive(id));
        assertFalse(error.getMessage().contains("synthetic-secret"));
        assertNull(error.getCause());
        assertError(source);
        assertArrayEquals(content, Files.readAllBytes(source));
        assertThrows(IllegalStateException.class, () -> service.unarchive(id));
        verify(service, times(1)).move(any(), any());
    }

    @Test
    void failedMovementPreservesPartialDestinationForManualReview() throws Exception {
        doAnswer(invocation -> {
            Files.writeString(destination, "partial");
            throw new IOException("synthetic interrupted move");
        }).when(service).move(source, destination);
        assertThrows(IOException.class, () -> service.unarchive(id));
        assertError(source);
        assertArrayEquals(content, Files.readAllBytes(source));
        assertEquals("partial", Files.readString(destination));
        verify(service, times(1)).move(source, destination);
    }

    @Test
    void rejectsInconsistentArchivedPathWithoutMovingOrChangingState() throws Exception {
        Evidence evidence = repository.findById(id).orElseThrow();
        evidence.setArchivedPath(null);
        repository.saveAndFlush(evidence);
        assertThrows(IllegalStateException.class, () -> service.unarchive(id));
        assertEquals(EvidenceStatus.ARQUIVADO, repository.findById(id).orElseThrow().getStatus());
        verify(service, never()).move(any(), any());
    }

    @Test
    void rejectsOverlappingStorageRoots() {
        assertThrows(IllegalArgumentException.class, () ->
                new UnarchivingService(repository, transactionManager, archive.toString(), archive.toString()));
        assertThrows(IllegalArgumentException.class, () ->
                new UnarchivingService(repository, transactionManager, directory.toString(), archive.toString()));
        assertThrows(IllegalArgumentException.class, () ->
                new UnarchivingService(repository, transactionManager, archive.resolve("fast").toString(), archive.toString()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void commitRollbackAtStartOrFinishDoesNotClaimSuccess(int failingCommit) throws Exception {
        PlatformTransactionManager manager = spy(transactionManager);
        AtomicInteger commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.incrementAndGet() == failingCommit) {
                transactionManager.rollback(invocation.getArgument(0));
                throw new TransactionSystemException("synthetic-secret");
            }
            return invocation.callRealMethod();
        }).when(manager).commit(any(TransactionStatus.class));
        UnarchivingService failing = new UnarchivingService(repository, manager, fast.toString(), archive.toString());
        assertThrows(IOException.class, () -> failing.unarchive(id));
        Path retained = failingCommit == 1 ? source : destination;
        assertError(retained);
        assertArrayEquals(content, Files.readAllBytes(retained));
        assertEquals(failingCommit == 1, Files.exists(source));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sqliteFailureAfterMovementPreservesCiphertextAndReportsUnconfirmedError(boolean failErrorToo) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl()); var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER fail_return BEFORE UPDATE ON evidence WHEN NEW.status = 'EM_ANALISE' "
                    + (failErrorToo ? "OR NEW.status = 'ERRO' " : "")
                    + "BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END");
            try {
                IOException error = assertThrows(IOException.class, () -> service.unarchive(id));
                assertArrayEquals(content, Files.readAllBytes(destination));
                assertFalse(Files.exists(source));
                if (failErrorToo) {
                    assertTrue(error.getMessage().contains("não foi possível confirmar ERRO"));
                    assertEquals("DESARQUIVANDO", sqlValue("status"));
                    assertEquals(source.toString(), sqlValue("current_path"));
                    assertMetadataPreserved(repository.findById(id).orElseThrow());
                } else assertError(destination);
                verify(service, times(1)).move(source, destination);
            } finally { sql.execute("DROP TRIGGER fail_return"); }
        }
    }

    @Test
    void concurrentCallCannotMoveTwiceOrSetOwnersStateToError() throws Exception {
        CountDownLatch moving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            moving.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(service).move(source, destination);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.unarchive(id));
            try {
                assertTrue(moving.await(10, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> service.unarchive(id));
                UnarchivingService other = new UnarchivingService(repository, transactionManager, fast.toString(), archive.toString());
                assertThrows(IllegalStateException.class, () -> other.unarchive(id));
                assertEquals("DESARQUIVANDO", sqlValue("status"));
            } finally { release.countDown(); }
            assertEquals(EvidenceStatus.EM_ANALISE, first.get(10, TimeUnit.SECONDS).getStatus());
        }
        verify(service, times(1)).move(source, destination);
    }

    @Test
    void conditionalClaimRejectsStaleReadWithoutMarkingCompetingOperationAsError() throws Exception {
        doAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Evidence current = repository.findById(id).orElseThrow();
                current.transitionTo(EvidenceStatus.DESARQUIVANDO);
                repository.saveAndFlush(current);
            });
            return invocation.callRealMethod();
        }).when(service).start(id, source.toString());
        assertThrows(IllegalStateException.class, () -> service.unarchive(id));
        assertEquals("DESARQUIVANDO", sqlValue("status"));
        verify(service, never()).move(any(), any());
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void rejectsMissingIdNullAndAmbientTransactionWithoutMovement() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.unarchive(Long.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> service.unarchive(null));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThrows(IllegalStateException.class, () -> service.unarchive(id)));
        verify(service, never()).move(any(), any());
        assertEquals(EvidenceStatus.ARQUIVADO, repository.findById(id).orElseThrow().getStatus());
    }

    private void assertError(Path currentPath) {
        Evidence saved = repository.findById(id).orElseThrow();
        assertEquals(EvidenceStatus.ERRO, saved.getStatus());
        assertEquals(currentPath.toString(), saved.getCurrentPath());
        assertNotNull(saved.getErrorMessage());
        assertMetadataPreserved(saved);
    }

    private void assertMetadataPreserved(Evidence saved) {
        assertEquals(archived.getArchivedPath(), saved.getArchivedPath());
        assertTrue(archived.getEncryptionPassword().equals(saved.getEncryptionPassword()));
        assertTrue(archived.getEncryptionKey().equals(saved.getEncryptionKey()));
        assertTrue(archived.getEncryptionIv().equals(saved.getEncryptionIv()));
        assertTrue(archived.getEncryptionSalt().equals(saved.getEncryptionSalt()));
        assertEquals(archived.getEncryptionIterations(), saved.getEncryptionIterations());
        assertEquals(archived.getEncryptionFormatVersion(), saved.getEncryptionFormatVersion());
        assertEquals(archived.getInformedHash(), saved.getInformedHash());
        assertEquals(archived.getCalculatedHash(), saved.getCalculatedHash());
        assertEquals(archived.getCreatedAt(), saved.getCreatedAt());
    }

    private static String databaseUrl() { return "jdbc:sqlite:" + databaseDirectory.resolve("unarchiving.db"); }

    private String sqlValue(String column) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.prepareStatement("select " + column + " from evidence where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }
}
