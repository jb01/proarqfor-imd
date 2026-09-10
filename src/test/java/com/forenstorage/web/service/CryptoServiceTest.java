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
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class CryptoServiceTest {
    @TempDir
    static Path databaseDirectory;
    @TempDir
    Path directory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("crypto.db"));
    }

    @Autowired
    EvidenceRepository repository;
    @Autowired
    PlatformTransactionManager transactionManager;

    private CryptoService service;
    private Path work;
    private Path archive;
    private Path dd;
    private Path zip;
    private byte[] zipContent;
    private Evidence evidence;

    @BeforeEach
    void setUp() throws IOException {
        work = Files.createDirectories(directory.resolve("storage/cold/work"));
        archive = directory.resolve("storage/cold/archive");
        dd = work.resolve("evidence.dd");
        byte[] content = new byte[100_000];
        new Random(42).nextBytes(content);
        Files.write(dd, content);
        zip = new ZipService(work.toString()).compress(dd);
        zipContent = Files.readAllBytes(zip);
        evidence = new Evidence(UUID.randomUUID().toString(), dd.toString(),
                "a".repeat(64), "a".repeat(64), EvidenceStatus.EM_ANALISE);
        evidence.transitionTo(EvidenceStatus.ARQUIVANDO);
        evidence = repository.saveAndFlush(evidence);
        service = spy(new CryptoService(repository, transactionManager, work.toString(), archive.toString()));
    }

    @Test
    void encryptsWithFinalTagAndCommitsSqliteMetadataBeforeRemovingZip(CapturedOutput output) throws Exception {
        doAnswer(invocation -> {
            // A fresh SQLite connection must see committed metadata before deletion.
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseDirectory.resolve("crypto.db"));
                 var statement = connection.prepareStatement(
                         "select encryption_key, encryption_iv, archived_path from evidence where id = ?")) {
                statement.setLong(1, evidence.getId());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertNotNull(rows.getString(1));
                    assertNotNull(rows.getString(2));
                    assertTrue(Files.isRegularFile(Path.of(rows.getString(3))));
                }
            }
            invocation.callRealMethod();
            return null;
        }).when(service).deleteZip(zip);

        Path encrypted = service.encrypt(evidence.getId(), zip);

        assertEquals(archive.resolve(evidence.getId().toString()).resolve("evidence.dd.zip.enc"), encrypted);
        assertFalse(Files.exists(zip));
        assertTrue(Files.isRegularFile(dd));
        assertEquals(zipContent.length + 16, Files.size(encrypted));
        Evidence saved = repository.findById(evidence.getId()).orElseThrow();
        assertEquals(encrypted.toString(), saved.getCurrentPath());
        assertEquals(encrypted.toString(), saved.getArchivedPath());
        assertEquals(EvidenceStatus.ARQUIVANDO, saved.getStatus());
        assertEquals(evidence.getInformedHash(), saved.getInformedHash());
        assertEquals(evidence.getCalculatedHash(), saved.getCalculatedHash());
        assertTrue(saved.getEncryptionPassword().matches("[A-Za-z0-9]{10}"));
        byte[] key = Base64.getDecoder().decode(saved.getEncryptionKey());
        byte[] iv = Base64.getDecoder().decode(saved.getEncryptionIv());
        byte[] salt = Base64.getDecoder().decode(saved.getEncryptionSalt());
        assertEquals(32, key.length);
        assertEquals(12, iv.length);
        assertEquals(16, salt.length);
        assertEquals(600_000, saved.getEncryptionIterations());
        assertEquals(1, saved.getEncryptionFormatVersion());
        var keySpec = new PBEKeySpec(saved.getEncryptionPassword().toCharArray(), salt, 600_000, 256);
        byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        assertTrue(Arrays.equals(derived, key), "A chave persistida deve corresponder à derivação aprovada");
        keySpec.clearPassword();
        // Independent one-shot ENCRYPTION verifies exact ciphertext and final GCM tag; no decryption.
        Cipher expected = Cipher.getInstance("AES/GCM/NoPadding");
        expected.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        assertArrayEquals(expected.doFinal(zipContent), Files.readAllBytes(encrypted));
        assertFalse(output.getAll().contains(saved.getEncryptionKey()));
        assertFalse(output.getAll().contains(saved.getEncryptionPassword()));
        assertFalse(saved.toString().contains(saved.getEncryptionKey()));
    }

    @Test
    void generatesNewIvSaltAndPasswordForEachEncryption() throws Exception {
        service.encrypt(evidence.getId(), zip);
        Evidence first = repository.findById(evidence.getId()).orElseThrow();
        Path secondDd = Files.writeString(work.resolve("second.dd"), "synthetic");
        Path secondZip = new ZipService(work.toString()).compress(secondDd);
        Evidence second = new Evidence(UUID.randomUUID().toString(), secondDd.toString(), "a", "a",
                EvidenceStatus.EM_ANALISE);
        second.transitionTo(EvidenceStatus.ARQUIVANDO);
        second = repository.saveAndFlush(second);
        service.encrypt(second.getId(), secondZip);
        second = repository.findById(second.getId()).orElseThrow();
        assertNotEquals(first.getEncryptionIv(), second.getEncryptionIv());
        assertNotEquals(first.getEncryptionSalt(), second.getEncryptionSalt());
        assertFalse(first.getEncryptionPassword().equals(second.getEncryptionPassword()));
    }

    enum Failure { WRITE, CLOSE, FINALIZATION }

    @Test
    void newAttemptAfterFinalizationFailureUsesNewIvAndSeparateTemporaryFile() throws Exception {
        var ivs = new ArrayList<byte[]>();
        var temporaries = new ArrayList<Path>();
        doAnswer(invocation -> {
            temporaries.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(service).openEncryptedOutput(any(Path.class));
        doAnswer(invocation -> {
            Cipher cipher = invocation.getArgument(0);
            ivs.add(cipher.getIV());
            if (ivs.size() == 1) {
                throw new BadPaddingException("simulated finalization failure");
            }
            return invocation.callRealMethod();
        }).when(service).finishEncryption(any(Cipher.class));
        assertThrows(IOException.class, () -> service.encrypt(evidence.getId(), zip));
        assertTrue(Files.exists(zip));
        service.encrypt(evidence.getId(), zip);
        assertFalse(Arrays.equals(ivs.get(0), ivs.get(1)));
        assertNotEquals(temporaries.get(0), temporaries.get(1));
        assertTrue(Files.exists(temporaries.get(0)), "A tentativa parcial é preservada");
    }

    @Test
    void commitFailureAfterFlushDoesNotAllowZipDeletion() throws Exception {
        PlatformTransactionManager failingManager = spy(transactionManager);
        doAnswer(invocation -> {
            transactionManager.rollback(invocation.getArgument(0));
            throw new TransactionSystemException("simulated commit failure");
        }).when(failingManager).commit(any(TransactionStatus.class));
        CryptoService failingService = new CryptoService(repository, failingManager, work.toString(), archive.toString());
        assertThrows(IOException.class, () -> failingService.encrypt(evidence.getId(), zip));
        assertArrayEquals(zipContent, Files.readAllBytes(zip));
        assertTrue(Files.isRegularFile(destination()));
        assertNull(repository.findById(evidence.getId()).orElseThrow().getEncryptionKey());
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void preservesZipAndDdWithoutPublishingOnEncryptionFailure(Failure failure) throws Exception {
        if (failure == Failure.FINALIZATION) {
            doThrow(new BadPaddingException("simulated finalization failure"))
                    .when(service).finishEncryption(any(Cipher.class));
        } else {
            doAnswer(invocation -> {
                OutputStream real = (OutputStream) invocation.callRealMethod();
                return new FilterOutputStream(real) {
                    @Override
                    public void write(byte[] bytes) throws IOException {
                        if (failure == Failure.WRITE) {
                            throw new IOException("simulated write failure");
                        }
                        out.write(bytes);
                    }

                    @Override
                    public void close() throws IOException {
                        super.close();
                        if (failure == Failure.CLOSE) {
                            throw new IOException("simulated close failure");
                        }
                    }
                };
            }).when(service).openEncryptedOutput(any(Path.class));
        }
        assertThrows(IOException.class, () -> service.encrypt(evidence.getId(), zip));
        assertArrayEquals(zipContent, Files.readAllBytes(zip));
        assertTrue(Files.isRegularFile(dd));
        assertFalse(Files.exists(destination()));
        assertNull(repository.findById(evidence.getId()).orElseThrow().getEncryptionKey());
        verify(service, never()).deleteZip(any());
    }

    @Test
    void sqliteFailurePreservesZipAndPublishedCiphertext() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseDirectory.resolve("crypto.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_crypto_metadata BEFORE UPDATE ON evidence "
                    + "WHEN NEW.encryption_key IS NOT NULL BEGIN SELECT RAISE(ABORT, 'simulated SQLite failure'); END");
            try {
                IOException error = assertThrows(IOException.class, () -> service.encrypt(evidence.getId(), zip));
                assertTrue(error.getMessage().contains("persistir metadados"));
                assertNull(error.getCause(), "Falhas SQL não devem expor parâmetros criptográficos");
                assertArrayEquals(zipContent, Files.readAllBytes(zip));
                assertTrue(Files.isRegularFile(destination()));
                assertTrue(Files.isRegularFile(dd));
                Evidence unchanged = repository.findById(evidence.getId()).orElseThrow();
                assertNull(unchanged.getEncryptionKey());
                assertNull(unchanged.getArchivedPath());
                assertEquals(dd.toString(), unchanged.getCurrentPath());
                verify(service, never()).deleteZip(any());
            } finally {
                statement.execute("DROP TRIGGER fail_crypto_metadata");
            }
        }
    }

    @Test
    void deletionFailurePreservesCommittedMetadataAndDoesNotReencrypt() throws Exception {
        doThrow(new IOException("simulated delete failure")).when(service).deleteZip(zip);
        assertThrows(IOException.class, () -> service.encrypt(evidence.getId(), zip));
        Evidence saved = repository.findById(evidence.getId()).orElseThrow();
        assertNotNull(saved.getEncryptionKey());
        assertTrue(Files.isRegularFile(destination()));
        assertArrayEquals(zipContent, Files.readAllBytes(zip));
        assertEquals(EvidenceStatus.ARQUIVANDO, saved.getStatus());
        byte[] ciphertext = Files.readAllBytes(destination());
        assertThrows(IllegalStateException.class, () -> service.encrypt(evidence.getId(), zip));
        assertArrayEquals(ciphertext, Files.readAllBytes(destination()));
    }

    @Test
    void refusesExistingDestinationWithoutOverwriting() throws Exception {
        Files.createDirectories(destination().getParent());
        Files.writeString(destination(), "foreign artifact");
        assertThrows(FileAlreadyExistsException.class, () -> service.encrypt(evidence.getId(), zip));
        assertEquals("foreign artifact", Files.readString(destination()));
        assertArrayEquals(zipContent, Files.readAllBytes(zip));
    }

    @Test
    void missingZipDoesNotCreateArchive() {
        Path missingDd = work.resolve("missing.dd");
        evidence.setCurrentPath(missingDd.toString());
        repository.saveAndFlush(evidence);
        assertThrows(IOException.class,
                () -> service.encrypt(evidence.getId(), work.resolve("missing.dd.zip")));
        assertFalse(Files.exists(archive));
    }

    @Test
    void rejectsUnrelatedZipAndPathOutsideWork() throws Exception {
        Path unrelated = Files.writeString(work.resolve("other.dd.zip"), "preserve");
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(evidence.getId(), unrelated));
        Path outside = Files.writeString(directory.resolve("outside.dd.zip"), "preserve");
        evidence.setCurrentPath(directory.resolve("outside.dd").toString());
        repository.saveAndFlush(evidence);
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(evidence.getId(), outside));
        assertEquals("preserve", Files.readString(unrelated));
        assertEquals("preserve", Files.readString(outside));
    }

    @Test
    void rejectsSourceAndArchiveDirectorySymlinks() throws Exception {
        Path link = Files.createSymbolicLink(work.resolve("link.dd.zip"), zip);
        evidence.setCurrentPath(work.resolve("link.dd").toString());
        repository.saveAndFlush(evidence);
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(evidence.getId(), link));
        evidence.setCurrentPath(dd.toString());
        repository.saveAndFlush(evidence);
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Files.createSymbolicLink(archive, outside);
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(evidence.getId(), zip));
        assertArrayEquals(zipContent, Files.readAllBytes(zip));
        try (var files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = "ARQUIVANDO", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryStateExceptArchiving(EvidenceStatus status) {
        Evidence blocked = new Evidence(UUID.randomUUID().toString(), dd.toString(), "a", "a",
                status == EvidenceStatus.HASH_DIVERGENTE ? status : EvidenceStatus.EM_ANALISE);
        if (status == EvidenceStatus.ARQUIVADO || status == EvidenceStatus.DESARQUIVANDO) {
            blocked.transitionTo(EvidenceStatus.ARQUIVANDO);
            blocked.transitionTo(EvidenceStatus.ARQUIVADO);
            if (status == EvidenceStatus.DESARQUIVANDO) {
                blocked.transitionTo(status);
            }
        } else if (status == EvidenceStatus.ERRO) {
            blocked.markError("synthetic failure");
        }
        Long id = repository.saveAndFlush(blocked).getId();
        assertThrows(IllegalStateException.class, () -> service.encrypt(id, zip));
        assertTrue(Files.exists(zip));
        assertFalse(Files.exists(archive));
    }

    @Test
    void refusesAmbientTransactionBeforeAnyFileOperation() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThrows(IllegalStateException.class, () -> service.encrypt(evidence.getId(), zip)));
        assertTrue(Files.exists(zip));
        assertFalse(Files.exists(archive));
    }

    private Path destination() {
        return archive.resolve(evidence.getId().toString()).resolve("evidence.dd.zip.enc");
    }
}
