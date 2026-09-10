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
        Objects.requireNonNull(evidenceId, "O id da evidência é obrigatório");
        Objects.requireNonNull(zipPath, "O caminho do ZIP é obrigatório");
        // Prevent an outer transaction from later rolling back metadata after deletion.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A cifra deve ser chamada fora de uma transação ativa");
        }
        Evidence evidence = repository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidência não encontrada"));
        requireArchiving(evidence);
        Path source = validateSource(evidence, zipPath);
        Path destinationDirectory = archiveStorage.resolve(evidenceId.toString());
        rejectSymlinks(destinationDirectory);
        Files.createDirectories(destinationDirectory);
        rejectSymlinks(destinationDirectory);
        Path destination = destinationDirectory.resolve(source.getFileName() + ".enc");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(destination.toString());
        }

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
            Path temporary = Files.createTempFile(destinationDirectory, ".encrypt-", ".part");
            try (var input = Files.newInputStream(source);
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
            // No REPLACE_EXISTING: foreign artifacts must never be overwritten.
            Files.move(temporary, destination);
            persistMetadata(evidenceId, evidence.getCurrentPath(), destination, iv, password, key, salt);
            deleteZip(source);
            return destination;
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha na criptografia; o ZIP foi preservado");
        } finally {
            keySpec.clearPassword();
            Arrays.fill(password, '\0');
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    private void persistMetadata(Long id, String expectedPath, Path destination, byte[] iv,
                                 char[] password, byte[] key, byte[] salt) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                Evidence current = repository.findById(id)
                        .orElseThrow(() -> new IllegalStateException("Evidência não encontrada"));
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

    private Path validateSource(Evidence evidence, Path zipPath) throws IOException {
        rejectSymlinks(zipPath.toAbsolutePath());
        Path source = zipPath.toAbsolutePath().normalize();
        Path dd = Path.of(evidence.getCurrentPath()).toAbsolutePath().normalize();
        if (!dd.startsWith(workStorage) || !dd.getFileName().toString().endsWith(".dd")
                || !source.equals(dd.resolveSibling(dd.getFileName() + ".zip"))) {
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
}
