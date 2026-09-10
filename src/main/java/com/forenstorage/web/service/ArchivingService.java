package com.forenstorage.web.service;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArchivingService {
    private final EvidenceRepository repository;
    private final StorageCopyService storage;
    private final ZipService zipService;
    private final CryptoService crypto;
    private final TransactionTemplate transaction;
    private final Set<Long> active = ConcurrentHashMap.newKeySet();

    public ArchivingService(EvidenceRepository repository, StorageCopyService storage, ZipService zipService,
                            CryptoService crypto, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storage = storage;
        this.zipService = zipService;
        this.crypto = crypto;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Evidence archive(Long evidenceId) throws IOException {
        Objects.requireNonNull(evidenceId, "O id da evidência é obrigatório");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("O arquivamento exige uma chamada fora de transação ativa");
        }
        if (!active.add(evidenceId)) {
            throw new IllegalStateException("Arquivamento já em andamento para esta evidência");
        }
        Operation operation = null;
        try {
            Evidence initial = load(evidenceId);
            if (initial.getStatus() != EvidenceStatus.EM_ANALISE || initial.getArchivedPath() != null) {
                throw new IllegalStateException("Arquivamento exige EM_ANALISE com .dd original; "
                        + "remova o registro e cadastre novamente um .dd quando necessário");
            }
            Path source = storage.validateSource(Path.of(initial.getCurrentPath()));
            operation = new Operation(initial, source);
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return advance(operation);
                } catch (ClaimRejected e) {
                    // A competing operation owns the state: do not retry or mark its record ERRO.
                    throw e;
                } catch (IOException | RuntimeException e) {
                    if (attempt == 1) {
                        String message = "Arquivamento falhou após duas tentativas na etapa: " + operation.stage;
                        markError(operation, message);
                        throw new IOException(message); // No nested exceptions/SQL/key material in user-visible errors.
                    }
                }
            }
            throw new IllegalStateException("Limite de tentativas inválido");
        } finally {
            if (operation != null && operation.encryption != null) operation.encryption.close();
            active.remove(evidenceId);
        }
    }

    private Evidence advance(Operation op) throws IOException {
        if (!op.started) {
            op.stage = "iniciar ARQUIVANDO";
            start(op.id, op.originalPath);
            op.started = true;
        }
        if (op.copy == null) {
            op.stage = "copiar fast para work";
            op.copy = storage.copy(op.id, op.source);
        }
        if (!op.workPersisted) {
            op.stage = "persistir path de work";
            persistWork(op.id, op.originalPath, op.copy.destination());
            op.workPersisted = true;
        }
        if (!op.sourceRemoved) {
            op.stage = "excluir origem confirmada de fast";
            storage.removeOriginal(op.copy);
            op.sourceRemoved = true;
        }
        if (op.zip == null) {
            op.stage = "compactar ZIP";
            Path dd = op.copy.destination();
            Path destination = dd.resolveSibling(dd.getFileName() + "." + UUID.randomUUID() + ".zip");
            op.zip = zipService.compress(dd, destination);
        }
        if (op.encrypted == null) {
            op.stage = "cifrar, publicar, persistir metadados e remover ZIP";
            if (op.encryption == null) op.encryption = crypto.prepare(op.id, op.zip, false);
            op.encrypted = crypto.complete(op.encryption);
        }
        op.stage = "confirmar ARQUIVADO";
        return finish(op.id, op.encrypted);
    }

    void start(Long id, String expectedPath) {
        transaction.executeWithoutResult(status -> {
            if (repository.claimArchiving(id, expectedPath, EvidenceStatus.EM_ANALISE, EvidenceStatus.ARQUIVANDO) != 1) {
                throw new ClaimRejected();
            }
        });
    }

    void persistWork(Long id, String originalPath, Path workPath) {
        transaction.executeWithoutResult(status -> {
            Evidence current = load(id);
            requireArchiving(current);
            if (!current.getCurrentPath().equals(originalPath) && !current.getCurrentPath().equals(workPath.toString())) {
                throw new IllegalStateException("Path da evidência alterado durante a operação");
            }
            current.setCurrentPath(workPath.toString());
            repository.saveAndFlush(current);
        });
    }

    Evidence finish(Long id, Path encrypted) {
        return transaction.execute(status -> {
            Evidence current = load(id);
            if (!encrypted.toString().equals(current.getArchivedPath())
                    || !encrypted.toString().equals(current.getCurrentPath())
                    || current.getEncryptionKey() == null || current.getEncryptionIv() == null) {
                throw new IllegalStateException("Metadados finais não confirmados");
            }
            if (current.getStatus() == EvidenceStatus.ARQUIVADO) return current;
            requireArchiving(current);
            current.transitionTo(EvidenceStatus.ARQUIVADO);
            return repository.saveAndFlush(current);
        });
    }

    private void markError(Operation op, String message) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                Evidence current = load(op.id);
                if (op.started) {
                    requireArchiving(current);
                } else if (current.getStatus() != EvidenceStatus.EM_ANALISE
                        || !op.originalPath.equals(current.getCurrentPath())) {
                    throw new IllegalStateException("Não foi possível confirmar a propriedade da operação");
                }
                current.markError(message);
                repository.saveAndFlush(current);
            });
        } catch (RuntimeException e) {
            throw new IOException("Falha de arquivamento e de persistência: não foi possível confirmar ERRO; "
                    + "artefatos preservados");
        }
    }

    private Evidence load(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Evidência não encontrada"));
    }

    private static void requireArchiving(Evidence evidence) {
        if (evidence.getStatus() != EvidenceStatus.ARQUIVANDO) {
            throw new IllegalStateException("A operação exige ARQUIVANDO");
        }
    }

    private static final class ClaimRejected extends IllegalStateException {
        private ClaimRejected() { super("Evidência alterada ou arquivamento já iniciado"); }
    }

    private static final class Operation {
        private final Long id;
        private final String originalPath;
        private final Path source;
        private String stage;
        private boolean started;
        private StorageCopyService.ConfirmedCopy copy;
        private boolean workPersisted;
        private boolean sourceRemoved;
        private Path zip;
        private CryptoService.Encryption encryption;
        private Path encrypted;

        private Operation(Evidence initial, Path source) {
            this.id = initial.getId();
            this.originalPath = initial.getCurrentPath();
            this.source = source;
        }
    }
}
