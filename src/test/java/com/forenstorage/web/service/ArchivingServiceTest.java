package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
@ExtendWith(OutputCaptureExtension.class)
class ArchivingServiceTest {
    @TempDir static Path databaseDirectory;
    @TempDir Path directory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("archiving.db"));
    }

    @Autowired EvidenceRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    private StorageCopyService storage;
    private ZipService zipService;
    private CryptoService crypto;
    private ArchivingService service;
    private Path source;
    private Path fast;
    private Path work;
    private Path archive;
    private byte[] content;
    private Long id;

    @BeforeEach
    void setUp() throws IOException {
        fast = Files.createDirectory(directory.resolve("fast"));
        work = directory.resolve("work");
        archive = directory.resolve("archive");
        content = new byte[16_000];
        new Random(42).nextBytes(content);
        source = Files.write(fast.resolve("synthetic.dd"), content);
        Evidence initial = new Evidence(UUID.randomUUID() + "/2026", source.toString(),
                "a".repeat(64), "a".repeat(64), EvidenceStatus.EM_ANALISE);
        id = repository.saveAndFlush(initial).getId();
        storage = spy(new StorageCopyService(fast.toString(), work.toString(), archive.toString()));
        zipService = spy(new ZipService(work.toString()));
        crypto = spy(new CryptoService(repository, transactionManager, work.toString(), archive.toString()));
        service = spy(new ArchivingService(repository, storage, zipService, crypto, transactionManager));
    }

    @Test
    void archivesInOrderWithCommittedPathsBeforeBothDeletions(CapturedOutput output) throws Exception {
        doAnswer(invocation -> {
            assertEquals("ARQUIVANDO", sqlValue("status"));
            Path committedWork = Path.of(sqlValue("current_path"));
            assertTrue(committedWork.startsWith(work));
            assertArrayEquals(content, Files.readAllBytes(committedWork));
            assertTrue(filesWithSuffix(archive, ".enc").isEmpty());
            return invocation.callRealMethod();
        }).when(storage).deleteOriginal(source);
        doAnswer(invocation -> {
            assertEquals("ARQUIVANDO", sqlValue("status"));
            assertTrue(Files.isRegularFile(Path.of(sqlValue("archived_path"))));
            assertNotNull(sqlValue("encryption_key"));
            assertNotNull(sqlValue("encryption_iv"));
            return invocation.callRealMethod();
        }).when(crypto).deleteZip(any());

        Evidence result = service.archive(id);

        assertSuccessful(result);
        var order = inOrder(service, storage, zipService, crypto);
        order.verify(service).start(eq(id), anyString());
        order.verify(storage).copy(id, source);
        order.verify(service).persistWork(eq(id), anyString(), any());
        order.verify(storage).deleteOriginal(source);
        order.verify(zipService).compress(any(), any());
        order.verify(crypto).finishEncryption(any());
        order.verify(crypto).publish(any(), any());
        order.verify(crypto).persistMetadata(eq(id), anyString(), any(), any(), any(), any(), any());
        order.verify(crypto).deleteZip(any());
        order.verify(service).finish(eq(id), any());
        assertFalse(output.getAll().contains(result.getEncryptionKey()));
        assertFalse(output.getAll().contains(result.getEncryptionPassword()));
    }

    enum Stage { START, COPY, WORK_METADATA, DELETE_FAST, ZIP, FINALIZATION, PUBLISH, CRYPTO_METADATA, DELETE_ZIP, FINISH }

    private void failAt(Stage stage, boolean once) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        org.mockito.stubbing.Answer<Object> answer = invocation -> {
            if (!once || calls.getAndIncrement() == 0) {
                if (stage == Stage.FINALIZATION) throw new BadPaddingException("synthetic-secret");
                if (stage == Stage.START || stage == Stage.WORK_METADATA || stage == Stage.FINISH) {
                    throw new IllegalStateException("synthetic-secret");
                }
                throw new IOException("synthetic-secret");
            }
            return invocation.callRealMethod();
        };
        switch (stage) {
            case START -> doAnswer(answer).when(service).start(anyLong(), anyString());
            case COPY -> doAnswer(answer).when(storage).copy(anyLong(), any());
            case WORK_METADATA -> doAnswer(answer).when(service).persistWork(anyLong(), anyString(), any());
            case DELETE_FAST -> doAnswer(answer).when(storage).deleteOriginal(any());
            case ZIP -> doAnswer(answer).when(zipService).compress(any(), any());
            case FINALIZATION -> doAnswer(answer).when(crypto).finishEncryption(any());
            case PUBLISH -> doAnswer(answer).when(crypto).publish(any(), any());
            case CRYPTO_METADATA -> doAnswer(answer).when(crypto).persistMetadata(anyLong(), anyString(), any(), any(), any(), any(), any());
            case DELETE_ZIP -> doAnswer(answer).when(crypto).deleteZip(any());
            case FINISH -> doAnswer(answer).when(service).finish(anyLong(), any());
        }
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void resumesOnlyPendingStepsAfterOneFailure(Stage stage) throws Exception {
        failAt(stage, true);
        assertSuccessful(service.archive(id));
        verify(storage, times(stage == Stage.COPY ? 2 : 1)).copy(id, source);
        verify(storage, times(stage == Stage.DELETE_FAST ? 2 : 1)).deleteOriginal(source);
        verify(zipService, times(stage == Stage.ZIP ? 2 : 1)).compress(any(), any());
        verify(crypto, times(stage == Stage.FINALIZATION ? 2 : 1)).finishEncryption(any());
        verify(crypto, times(stage == Stage.PUBLISH ? 2 : 1)).publish(any(), any());
        verify(crypto, times(stage == Stage.DELETE_ZIP ? 2 : 1)).deleteZip(any());
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void secondFailurePersistsTerminalErrorAndPreservesLastUsableCopy(Stage stage) throws Exception {
        failAt(stage, false);
        IOException error = assertThrows(IOException.class, () -> service.archive(id));
        Evidence saved = repository.findById(id).orElseThrow();
        assertEquals(EvidenceStatus.ERRO, saved.getStatus());
        assertEquals(error.getMessage(), saved.getErrorMessage());
        assertFalse(error.getMessage().contains("synthetic-secret"));
        assertNull(error.getCause());
        assertThrows(IllegalStateException.class, () -> service.archive(id));
        if (stage.ordinal() <= Stage.DELETE_FAST.ordinal()) {
            assertArrayEquals(content, Files.readAllBytes(source));
        } else {
            assertFalse(Files.exists(source));
            assertArrayEquals(content, Files.readAllBytes(filesWithSuffix(work, ".dd").getFirst()));
        }
        if (stage == Stage.DELETE_ZIP || stage == Stage.FINISH || stage == Stage.CRYPTO_METADATA) {
            assertEquals(1, filesWithSuffix(archive, ".enc").size());
            verify(crypto, times(1)).finishEncryption(any());
        }
        if (stage == Stage.COPY) verify(storage, times(2)).copy(id, source);
        if (stage == Stage.ZIP) verify(zipService, times(2)).compress(any(), any());
        if (stage == Stage.FINALIZATION) verify(crypto, times(2)).finishEncryption(any());
        if (stage == Stage.DELETE_FAST) verify(storage, times(2)).deleteOriginal(source);
    }

    @Test
    void failureBudgetIsGlobalAcrossDifferentStages() throws Exception {
        failAt(Stage.ZIP, true);
        failAt(Stage.FINALIZATION, true);
        assertThrows(IOException.class, () -> service.archive(id));
        assertEquals(EvidenceStatus.ERRO, repository.findById(id).orElseThrow().getStatus());
        verify(zipService, times(2)).compress(any(), any());
        verify(crypto, times(1)).finishEncryption(any());
        assertFalse(Files.exists(source));
        assertEquals(1, filesWithSuffix(work, ".zip").size());
    }

    @Test
    void failedZipAfterSourceRemovalUsesNewZipAndPreservesPartial() throws Exception {
        Path[] partial = new Path[1];
        doAnswer(invocation -> {
            assertFalse(Files.exists(source));
            partial[0] = invocation.getArgument(1);
            Files.writeString(partial[0], "partial");
            throw new IOException("synthetic partial ZIP");
        }).doCallRealMethod().when(zipService).compress(any(), any());
        Evidence result = service.archive(id);
        assertEquals(EvidenceStatus.ARQUIVADO, result.getStatus());
        assertEquals("partial", Files.readString(partial[0]));
        verify(storage, times(1)).copy(id, source);
        verify(storage, times(1)).deleteOriginal(source);
        assertArrayEquals(content, Files.readAllBytes(filesWithSuffix(work, ".dd").getFirst()));
    }

    @Test
    void freshIvAndTemporaryFileWhenCipherMustRunAgain() throws Exception {
        var ivs = new ArrayList<byte[]>();
        doAnswer(invocation -> {
            Cipher cipher = invocation.getArgument(0);
            ivs.add(cipher.getIV());
            if (ivs.size() == 1) throw new BadPaddingException("synthetic finalization failure");
            return invocation.callRealMethod();
        }).when(crypto).finishEncryption(any());
        assertSuccessful(service.archive(id));
        assertFalse(Arrays.equals(ivs.get(0), ivs.get(1)));
        assertEquals(1, filesWithSuffix(archive, ".part").size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void rollbackAtStartWorkOrFinalCommitIsRetriedSafely(int failingCommit) throws Exception {
        PlatformTransactionManager manager = spy(transactionManager);
        AtomicInteger count = new AtomicInteger();
        doAnswer(invocation -> {
            if (count.incrementAndGet() == failingCommit) {
                transactionManager.rollback(invocation.getArgument(0));
                throw new TransactionSystemException("synthetic commit failure");
            }
            return invocation.callRealMethod();
        }).when(manager).commit(any(TransactionStatus.class));
        ArchivingService transactional = new ArchivingService(repository, storage, zipService, crypto, manager);
        assertSuccessful(transactional.archive(id));
        verify(storage, times(1)).copy(id, source);
        verify(crypto, times(1)).finishEncryption(any());
    }

    @Test
    void sqliteMetadataFailureRetriesSamePublishedCiphertextAndParameters() throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl()); var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER fail_metadata BEFORE UPDATE ON evidence WHEN NEW.encryption_key IS NOT NULL "
                    + "BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END");
            byte[][] published = new byte[1][];
            byte[][] firstIv = new byte[1][];
            doAnswer(invocation -> {
                Path destination = invocation.getArgument(2);
                published[0] = Files.readAllBytes(destination);
                firstIv[0] = ((byte[]) invocation.getArgument(3)).clone();
                try { return invocation.callRealMethod(); }
                finally { sql.execute("DROP TRIGGER fail_metadata"); }
            }).doCallRealMethod().when(crypto).persistMetadata(anyLong(), anyString(), any(), any(), any(), any(), any());
            Evidence result = service.archive(id);
            assertSuccessful(result);
            assertArrayEquals(published[0], Files.readAllBytes(Path.of(result.getArchivedPath())));
            assertArrayEquals(firstIv[0], java.util.Base64.getDecoder().decode(result.getEncryptionIv()));
            verify(crypto, times(1)).finishEncryption(any());
            verify(crypto, times(2)).persistMetadata(anyLong(), anyString(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void sqliteFailureToSaveErrorDoesNotClaimErrorWasPersisted() throws Exception {
        failAt(Stage.ZIP, false);
        try (var connection = DriverManager.getConnection(databaseUrl()); var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER fail_error BEFORE UPDATE ON evidence WHEN NEW.status = 'ERRO' "
                    + "BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END");
            try {
                IOException error = assertThrows(IOException.class, () -> service.archive(id));
                assertTrue(error.getMessage().contains("não foi possível confirmar ERRO"));
                assertEquals(EvidenceStatus.ARQUIVANDO, repository.findById(id).orElseThrow().getStatus());
                assertArrayEquals(content, Files.readAllBytes(filesWithSuffix(work, ".dd").getFirst()));
                verify(zipService, times(2)).compress(any(), any());
            } finally { sql.execute("DROP TRIGGER fail_error"); }
        }
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = "EM_ANALISE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsIneligibleStatesWithoutFileOperations(EvidenceStatus status) throws Exception {
        Evidence blocked = new Evidence(UUID.randomUUID().toString(), source.toString(), "a", "a",
                status == EvidenceStatus.HASH_DIVERGENTE ? status : EvidenceStatus.EM_ANALISE);
        if (status == EvidenceStatus.ERRO) blocked.markError("synthetic");
        else if (status != EvidenceStatus.HASH_DIVERGENTE) {
            blocked.transitionTo(EvidenceStatus.ARQUIVANDO);
            if (status != EvidenceStatus.ARQUIVANDO) blocked.transitionTo(EvidenceStatus.ARQUIVADO);
            if (status == EvidenceStatus.DESARQUIVANDO) blocked.transitionTo(EvidenceStatus.DESARQUIVANDO);
        }
        Long blockedId = repository.saveAndFlush(blocked).getId();
        assertThrows(IllegalStateException.class, () -> service.archive(blockedId));
        verifyNoInteractions(storage, zipService, crypto);
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void rejectsMissingOutsideSymlinkDirectoryAndReturnedCiphertext() throws Exception {
        Path outside = Files.writeString(directory.resolve("outside.dd"), "preserve");
        Path link = Files.createSymbolicLink(fast.resolve("link.dd"), source);
        Path folder = Files.createDirectory(fast.resolve("folder.dd"));
        Path returned = Files.writeString(fast.resolve("returned.zip.enc"), "preserve");
        for (Path invalid : List.of(outside, link, folder, returned, fast.resolve("missing.dd"))) {
            Evidence current = repository.findById(id).orElseThrow();
            current.setCurrentPath(invalid.toString());
            repository.saveAndFlush(current);
            assertThrows(Exception.class, () -> service.archive(id));
            assertEquals(EvidenceStatus.EM_ANALISE, repository.findById(id).orElseThrow().getStatus());
        }
        verify(storage, never()).copy(anyLong(), any());
        assertArrayEquals(content, Files.readAllBytes(source));
        assertEquals("preserve", Files.readString(outside));
        assertEquals("preserve", Files.readString(returned));
    }

    @Test
    void concurrentCallsCannotProcessSameEvidenceTwice() throws Exception {
        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            copying.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(storage).copy(id, source);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.archive(id));
            try {
                assertTrue(copying.await(10, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> service.archive(id));
                ArchivingService other = new ArchivingService(repository, storage, zipService, crypto, transactionManager);
                assertThrows(IllegalStateException.class, () -> other.archive(id));
            } finally { release.countDown(); }
            assertSuccessful(first.get(10, TimeUnit.SECONDS));
        }
        verify(storage, times(1)).copy(id, source);
    }

    @Test
    void rejectsAmbientTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThrows(IllegalStateException.class, () -> service.archive(id)));
        verifyNoInteractions(storage, zipService, crypto);
    }

    private void assertSuccessful(Evidence result) throws IOException {
        Evidence saved = repository.findById(id).orElseThrow();
        assertEquals(EvidenceStatus.ARQUIVADO, result.getStatus());
        assertEquals(EvidenceStatus.ARQUIVADO, saved.getStatus());
        assertFalse(Files.exists(source));
        assertEquals(1, filesWithSuffix(work, ".dd").size());
        assertArrayEquals(content, Files.readAllBytes(filesWithSuffix(work, ".dd").getFirst()));
        assertTrue(filesWithSuffix(work, ".zip").isEmpty());
        assertEquals(1, filesWithSuffix(archive, ".enc").size());
        assertEquals(saved.getCurrentPath(), saved.getArchivedPath());
        assertEquals("a".repeat(64), saved.getInformedHash());
        assertEquals("a".repeat(64), saved.getCalculatedHash());
        assertNotNull(saved.getEncryptionKey());
        assertNotNull(saved.getEncryptionIv());
    }

    private static List<Path> filesWithSuffix(Path root, String suffix) throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).toList();
        }
    }

    private static String databaseUrl() { return "jdbc:sqlite:" + databaseDirectory.resolve("archiving.db"); }

    private String sqlValue(String column) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.prepareStatement("select " + column + " from evidence where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }
}
