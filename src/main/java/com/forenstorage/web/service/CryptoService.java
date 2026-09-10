package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

@Service
public class CryptoService {
    private static final int ITERATIONS = 600_000;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();
    private final EvidenceRepository repository;
    private final TransactionTemplate transaction;
    private final Path workStorage;
    private final Path archiveStorage;

    public CryptoService(EvidenceRepository repository, PlatformTransactionManager transactionManager,
                         @Value("${forenstorage.storage.work:storage/cold/work}") String workStorage,
                         @Value("${forenstorage.storage.archive:storage/cold/archive}") String archiveStorage) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.workStorage = Path.of(workStorage).toAbsolutePath().normalize();
        this.archiveStorage = Path.of(archiveStorage).toAbsolutePath().normalize();
        if (this.workStorage.startsWith(this.archiveStorage) || this.archiveStorage.startsWith(this.workStorage)) {
            throw new IllegalArgumentException("Work e archive devem ser diretórios separados");
        }
    }

    /**
     * Cifra somente o ZIP da cópia de trabalho de uma evidência em ARQUIVANDO.
     * O chamador deve ter confirmado a etapa ZIP. Não inicia/conclui estados nem executa retry.
     * Arquivo final e metadados comprometidos precedem a exclusão do ZIP.
     */
    public Path encrypt(Long evidenceId, Path zipPath) throws IOException {
        try (Encryption operation = prepare(evidenceId, zipPath, true)) {
            return complete(operation);
        }
    }

    Encryption prepare(Long evidenceId, Path zipPath, boolean strictSibling) throws IOException {
        Objects.requireNonNull(evidenceId, "O id da evidência é obrigatório");
        Objects.requireNonNull(zipPath, "O caminho do ZIP é obrigatório");
        // Prevent an outer transaction from later rolling back metadata after deletion.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A cifra deve ser chamada fora de uma transação ativa");
        }
        Evidence evidence = repository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidência não encontrada"));
        requireArchiving(evidence);
        Path source = validateSource(evidence, zipPath, strictSibling);
        Path destinationDirectory = archiveStorage.resolve(evidenceId.toString());
        rejectSymlinks(destinationDirectory);
        Files.createDirectories(destinationDirectory);
        rejectSymlinks(destinationDirectory);
        Path destination = destinationDirectory.resolve(source.getFileName() + ".enc");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(destination.toString());
        }
        return new Encryption(evidenceId, evidence.getCurrentPath(), source, destination);
    }

    Path complete(Encryption operation) throws IOException {
        if (operation.closed) {
            throw new IllegalStateException("Contexto de cifra encerrado");
        }
        if (operation.temporary == null) {
            encryptTemporary(operation);
        }
        if (!operation.published) {
            publish(operation.temporary, operation.destination);
            operation.published = true;
        }
        if (!operation.persisted) {
            persistMetadata(operation.id, operation.expectedPath, operation.destination, operation.iv,
                    operation.password, operation.key, operation.salt);
            operation.persisted = true;
        }
        if (!operation.zipDeleted) {
            StorageCopyService.regularUnder(operation.source, workStorage);
            StorageCopyService.regularUnder(operation.destination, archiveStorage);
            deleteZip(operation.source);
            operation.zipDeleted = true;
        }
        return operation.destination;
    }

    private void encryptTemporary(Encryption operation) throws IOException {
        byte[] iv = new byte[12];
        byte[] salt = new byte[16];
        random.nextBytes(iv);
        random.nextBytes(salt);
        char[] password = new char[10];
        for (int i = 0; i < password.length; i++) {
            password[i] = ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length()));
        }
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            Path temporary = Files.createTempFile(operation.destination.getParent(), ".encrypt-", ".part");
            try (var input = Files.newInputStream(operation.source);
                 var output = openEncryptedOutput(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    byte[] encrypted = cipher.update(buffer, 0, count);
                    if (encrypted != null) {
                        output.write(encrypted);
                    }
                }
                output.write(finishEncryption(cipher));
            }
            // Transfer ownership only after doFinal AND both stream closes succeeded.
            operation.key = key;
            operation.password = password;
            operation.iv = iv;
            operation.salt = salt;
            operation.temporary = temporary;
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha na criptografia; o ZIP foi preservado");
        } finally {
            keySpec.clearPassword();
            if (operation.temporary == null) {
                Arrays.fill(password, '\0');
                if (key != null) {
                    Arrays.fill(key, (byte) 0);
                }
            }
        }
    }

    void persistMetadata(Long id, String expectedPath, Path destination, byte[] iv,
                                 char[] password, byte[] key, byte[] salt) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                Evidence current = repository.findById(id)
                        .orElseThrow(() -> new IllegalStateException("Evidência não encontrada"));
                // A commit may have succeeded before reporting failure. Only recognize OUR exact metadata.
                if (current.getStatus() == EvidenceStatus.ARQUIVANDO
                        && destination.toString().equals(current.getArchivedPath())
                        && destination.toString().equals(current.getCurrentPath())
                        && Base64.getEncoder().encodeToString(key).equals(current.getEncryptionKey())
                        && Base64.getEncoder().encodeToString(iv).equals(current.getEncryptionIv())
                        && Base64.getEncoder().encodeToString(salt).equals(current.getEncryptionSalt())
                        && new String(password).equals(current.getEncryptionPassword())
                        && Integer.valueOf(ITERATIONS).equals(current.getEncryptionIterations())
                        && Integer.valueOf(1).equals(current.getEncryptionFormatVersion())) {
                    return;
                }
                requireArchiving(current);
                if (!current.getCurrentPath().equals(expectedPath)) {
                    throw new IllegalStateException("O path da evidência foi alterado");
                }
                Base64.Encoder encoder = Base64.getEncoder();
                current.recordEncryption(destination.toString(), encoder.encodeToString(iv), new String(password),
                        encoder.encodeToString(key), encoder.encodeToString(salt), ITERATIONS, 1);
                repository.saveAndFlush(current);
            });
        } catch (RuntimeException e) {
            // Do not propagate SQL/parameter-bearing causes that could disclose key material.
            throw new IOException("Falha ao persistir metadados da cifra; ZIP e cifrado foram preservados");
        }
    }

    private Path validateSource(Evidence evidence, Path zipPath, boolean strictSibling) throws IOException {
        rejectSymlinks(zipPath.toAbsolutePath());
        Path source = zipPath.toAbsolutePath().normalize();
        Path dd = Path.of(evidence.getCurrentPath()).toAbsolutePath().normalize();
        if (!dd.startsWith(workStorage) || !dd.getFileName().toString().endsWith(".dd")
                || !source.getParent().equals(dd.getParent())
                || !source.getFileName().toString().startsWith(dd.getFileName() + ".")
                || !source.getFileName().toString().endsWith(".zip")
                || (strictSibling && !source.equals(dd.resolveSibling(dd.getFileName() + ".zip")))) {
            throw new IllegalArgumentException("O ZIP deve corresponder à cópia .dd da evidência em work");
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(source)) {
            throw new IOException("ZIP inexistente ou não regular/legível");
        }
        Path real = source.toRealPath();
        if (!real.startsWith(workStorage.toRealPath())) {
            throw new IllegalArgumentException("O ZIP deve estar dentro de storage/cold/work");
        }
        return real;
    }

    private static void requireArchiving(Evidence evidence) {
        if (evidence.getStatus() != EvidenceStatus.ARQUIVANDO || evidence.getArchivedPath() != null) {
            throw new IllegalStateException("A cifra exige ARQUIVANDO sem artefato já publicado");
        }
    }

    private static void rejectSymlinks(Path path) {
        Path component = path.getRoot();
        for (Path part : path) {
            component = component.resolve(part);
            if (Files.isSymbolicLink(component)) {
                throw new IllegalArgumentException("Links simbólicos não são permitidos");
            }
        }
    }

    // Package-level I/O seams permit deterministic failure tests using synthetic files only.
    OutputStream openEncryptedOutput(Path temporary) throws IOException {
        return Files.newOutputStream(temporary, StandardOpenOption.WRITE);
    }

    byte[] finishEncryption(Cipher cipher) throws GeneralSecurityException {
        return cipher.doFinal();
    }

    void deleteZip(Path source) throws IOException {
        Files.delete(source);
    }

    void publish(Path temporary, Path destination) throws IOException {
        rejectSymlinks(destination.getParent());
        Files.move(temporary, destination); // Never REPLACE_EXISTING.
    }

    // Only the synchronous owner retains this context. No generated toString exposing secrets.
    static final class Encryption implements AutoCloseable {
        private final Long id;
        private final String expectedPath;
        private final Path source;
        private final Path destination;
        private Path temporary;
        private byte[] key;
        private char[] password;
        private byte[] iv;
        private byte[] salt;
        private boolean published;
        private boolean persisted;
        private boolean zipDeleted;
        private boolean closed;

        private Encryption(Long id, String expectedPath, Path source, Path destination) {
            this.id = id;
            this.expectedPath = expectedPath;
            this.source = source;
            this.destination = destination;
        }

        @Override
        public void close() {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
            closed = true;
        }
    }
}
