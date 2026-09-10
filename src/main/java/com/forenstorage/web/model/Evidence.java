package com.forenstorage.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "evidence", indexes = @Index(
        name = "uk_evidence_identifier", columnList = "evidence_identifier", unique = true))
public class Evidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_identifier", nullable = false, updatable = false)
    private String evidenceIdentifier;

    @Column(nullable = false)
    private String currentPath;

    @Column(nullable = false, updatable = false, length = 64)
    private String informedHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String calculatedHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus status;

    private String archivedPath;
    private String encryptionIv;
    private String encryptionPassword;
    private String encryptionKey;
    private String encryptionSalt;
    private Integer encryptionIterations;
    private Integer encryptionFormatVersion;
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Evidence() {
        // Required by JPA; domain callers use the validated constructor.
    }

    public Evidence(String evidenceIdentifier, String currentPath, String informedHash,
                    String calculatedHash, EvidenceStatus initialStatus) {
        if (initialStatus != EvidenceStatus.EM_ANALISE
                && initialStatus != EvidenceStatus.HASH_DIVERGENTE) {
            throw new IllegalArgumentException("Estado inicial deve ser EM_ANALISE ou HASH_DIVERGENTE");
        }
        this.evidenceIdentifier = required(evidenceIdentifier, "Identificador");
        this.currentPath = required(currentPath, "Path atual");
        this.informedHash = required(informedHash, "Hash informado");
        this.calculatedHash = required(calculatedHash, "Hash calculado");
        this.status = initialStatus;
    }

    @PrePersist
    private void recordCreationTime() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void transitionTo(EvidenceStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transição não permitida: " + status + " -> " + target);
        }
        status = target;
    }

    public void markError(String message) {
        String validatedMessage = required(message, "Mensagem de erro");
        transitionTo(EvidenceStatus.ERRO);
        errorMessage = validatedMessage;
    }

    public void setCurrentPath(String currentPath) {
        this.currentPath = required(currentPath, "Path atual");
    }

    public void setArchivedPath(String archivedPath) { this.archivedPath = archivedPath; }
    public void setEncryptionIv(String encryptionIv) { this.encryptionIv = encryptionIv; }

    // Academic-only storage of the key alongside its metadata. Never include these fields in toString/logs.
    public void recordEncryption(String path, String iv, String password, String key,
                                 String salt, int iterations, int formatVersion) {
        if (status != EvidenceStatus.ARQUIVANDO || archivedPath != null) {
            throw new IllegalStateException("A evidência não está disponível para registrar a cifra");
        }
        archivedPath = required(path, "Path cifrado");
        encryptionIv = required(iv, "IV");
        encryptionPassword = required(password, "Senha");
        encryptionKey = required(key, "Chave");
        encryptionSalt = required(salt, "Salt");
        encryptionIterations = iterations;
        encryptionFormatVersion = formatVersion;
        currentPath = archivedPath;
    }

    public Long getId() { return id; }
    public String getEvidenceIdentifier() { return evidenceIdentifier; }
    public String getCurrentPath() { return currentPath; }
    public String getInformedHash() { return informedHash; }
    public String getCalculatedHash() { return calculatedHash; }
    public EvidenceStatus getStatus() { return status; }
    public String getArchivedPath() { return archivedPath; }
    public String getEncryptionIv() { return encryptionIv; }
    public String getEncryptionPassword() { return encryptionPassword; }
    public String getEncryptionKey() { return encryptionKey; }
    public String getEncryptionSalt() { return encryptionSalt; }
    public Integer getEncryptionIterations() { return encryptionIterations; }
    public Integer getEncryptionFormatVersion() { return encryptionFormatVersion; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " é obrigatório");
        }
        return value;
    }
}
