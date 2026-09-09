package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@Service
public class EvidenceRegistrationService {
    private final EvidenceRepository repository;
    private final HashService hashService;
    private final Path fastStorage;

    public EvidenceRegistrationService(EvidenceRepository repository, HashService hashService,
                                      @Value("${forenstorage.storage.fast:storage/fast}") String fastStorage) {
        this.repository = repository;
        this.hashService = hashService;
        this.fastStorage = Path.of(fastStorage).toAbsolutePath().normalize();
    }

    public Evidence register(String evidenceIdentifier, Path currentPath, String informedHash) throws IOException {
        if (evidenceIdentifier == null || evidenceIdentifier.isBlank()) {
            throw new IllegalArgumentException("Identificador da evidência é obrigatório");
        }
        if (informedHash == null || !informedHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Hash SHA-256 deve conter 64 dígitos hexadecimais");
        }
        if (repository.existsByEvidenceIdentifier(evidenceIdentifier)) {
            throw duplicate(evidenceIdentifier, null);
        }
        Path file = validatePath(currentPath);
        String calculatedHash = hashService.calculateSha256(file);
        EvidenceStatus status = informedHash.equalsIgnoreCase(calculatedHash)
                ? EvidenceStatus.EM_ANALISE : EvidenceStatus.HASH_DIVERGENTE;
        Evidence evidence = new Evidence(evidenceIdentifier, file.toString(), informedHash, calculatedHash, status);
        try {
            // Repository transaction starts only after the file has been fully read.
            // The unique SQLite index also rejects a duplicate inserted after the precheck.
            return repository.saveAndFlush(evidence);
        } catch (DataAccessException e) {
            String detail = e.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("SQLITE_CONSTRAINT_UNIQUE")
                    && detail.contains("evidence.evidence_identifier")) {
                throw duplicate(evidenceIdentifier, e);
            }
            throw e;
        }
    }

    private Path validatePath(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path atual é obrigatório");
        }
        Path absolute = path.toAbsolutePath();
        // Inspect before normalization so a symlink followed by '..' is not hidden.
        Path component = absolute.getRoot();
        for (Path part : absolute) {
            component = component.resolve(part);
            if (Files.isSymbolicLink(component)) {
                throw new IllegalArgumentException("Links simbólicos não são permitidos: " + path);
            }
        }
        Path normalized = absolute.normalize();
        if (!normalized.startsWith(fastStorage) || normalized.equals(fastStorage)) {
            throw new IllegalArgumentException("O arquivo deve estar dentro de storage/fast: " + path);
        }
        if (!normalized.getFileName().toString().endsWith(".dd")) {
            throw new IllegalArgumentException("A evidência deve ser um arquivo .dd: " + path);
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo não encontrado: " + path);
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(normalized)) {
            throw new IOException("A evidência deve ser um arquivo regular e legível: " + path);
        }
        Path real = normalized.toRealPath();
        if (!real.startsWith(fastStorage.toRealPath())) {
            throw new IllegalArgumentException("O arquivo deve estar dentro de storage/fast: " + path);
        }
        return real;
    }

    private IllegalArgumentException duplicate(String identifier, Throwable cause) {
        return new IllegalArgumentException("Identificador de evidência já cadastrado: " + identifier, cause);
    }
}
