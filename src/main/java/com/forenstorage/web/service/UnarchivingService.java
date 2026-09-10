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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Simulates return to analysis by moving ciphertext; never restores plaintext. */
@Service
public class UnarchivingService {
    private final EvidenceRepository repository;
    private final TransactionTemplate transaction;
    private final Path fast;
    private final Path archive;
    private final Set<Long> active = ConcurrentHashMap.newKeySet();

    public UnarchivingService(EvidenceRepository repository, PlatformTransactionManager transactionManager,
                              @Value("${forenstorage.storage.fast:storage/fast}") String fast,
                              @Value("${forenstorage.storage.archive:storage/cold/archive}") String archive) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.fast = Path.of(fast).toAbsolutePath().normalize();
        this.archive = Path.of(archive).toAbsolutePath().normalize();
        if (this.fast.startsWith(this.archive) || this.archive.startsWith(this.fast)) {
            throw new IllegalArgumentException("As raízes fast e archive devem ser separadas");
        }
    }

    public Evidence unarchive(Long evidenceId) throws IOException {
        Objects.requireNonNull(evidenceId, "O id da evidência é obrigatório");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("O desarquivamento exige uma chamada fora de transação ativa");
        }
        if (!active.add(evidenceId)) {
            throw new IllegalStateException("Desarquivamento já em andamento para esta evidência");
        }
        try {
            Evidence initial = load(evidenceId);
            if (initial.getStatus() != EvidenceStatus.ARQUIVADO
                    || !initial.getCurrentPath().equals(initial.getArchivedPath())) {
                throw new IllegalStateException("Desarquivamento simulado exige uma evidência ARQUIVADO");
            }
            String original = initial.getCurrentPath();
            boolean started = false;
            Path moved = null;
            String stage = "persistir DESARQUIVANDO";
            try {
                start(evidenceId, original);
                started = true;
                stage = "mover arquivo cifrado de archive para fast";
                Path source = StorageCopyService.regularUnder(Path.of(original), archive);
                if (!source.getParent().equals(archive.resolve(evidenceId.toString()))
                        || !source.getFileName().toString().endsWith(".zip.enc")) {
                    throw new IllegalArgumentException("Arquivo cifrado inválido para esta evidência");
                }
                Path destinationDirectory = fast.resolve(evidenceId.toString());
                StorageCopyService.rejectSymlinks(destinationDirectory);
                Files.createDirectories(destinationDirectory);
                StorageCopyService.rejectSymlinks(destinationDirectory);
                Path destination = destinationDirectory.resolve(source.getFileName());
                StorageCopyService.rejectSymlinks(destination);
                move(source, destination);
                moved = destination;
                stage = "persistir path de fast e EM_ANALISE";
                return finish(evidenceId, original, destination);
            } catch (ClaimRejected e) {
                // Another caller owns the transition; never mark its operation as failed.
                throw e;
            } catch (IOException | RuntimeException e) {
                String message = "Desarquivamento simulado falhou na etapa: " + stage;
                markError(evidenceId, original, started, moved, message);
                throw new IOException(message); // Do not expose SQL, keys or nested exception messages.
            }
        } finally {
            active.remove(evidenceId);
        }
    }

    void start(Long id, String original) {
        transaction.executeWithoutResult(status -> {
            if (repository.claimUnarchiving(id, original, EvidenceStatus.ARQUIVADO,
                    EvidenceStatus.DESARQUIVANDO) != 1) throw new ClaimRejected();
        });
    }

    void move(Path source, Path destination) throws IOException {
        Files.move(source, destination); // No REPLACE_EXISTING: preserve unknown destinations.
    }

    Evidence finish(Long id, String original, Path destination) {
        return transaction.execute(status -> {
            Evidence current = load(id);
            requireOwned(current, original, EvidenceStatus.DESARQUIVANDO);
            current.setCurrentPath(destination.toString());
            current.transitionTo(EvidenceStatus.EM_ANALISE);
            return repository.saveAndFlush(current);
        });
    }

    private void markError(Long id, String original, boolean started, Path moved, String message) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                Evidence current = load(id);
                requireOwned(current, original, started ? EvidenceStatus.DESARQUIVANDO : EvidenceStatus.ARQUIVADO);
                if (moved != null) current.setCurrentPath(moved.toString());
                current.markError(message);
                repository.saveAndFlush(current);
            });
        } catch (RuntimeException e) {
            throw new IOException(message + "; não foi possível confirmar ERRO no SQLite. "
                    + "Artefatos preservados; localização e estado exigem avaliação manual");
        }
    }

    private void requireOwned(Evidence evidence, String original, EvidenceStatus expected) {
        if (evidence.getStatus() != expected || !original.equals(evidence.getCurrentPath())
                || !original.equals(evidence.getArchivedPath())) {
            throw new IllegalStateException("Evidência alterada durante o desarquivamento");
        }
    }

    private Evidence load(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Evidência não encontrada"));
    }

    private static final class ClaimRejected extends IllegalStateException {
        private ClaimRejected() { super("Desarquivamento já iniciado ou evidência alterada"); }
    }
}
