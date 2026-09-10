package com.forenstorage.web.service;

import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class EvidenceRemovalService {
    private static final Set<EvidenceStatus> REMOVABLE = Set.of(EvidenceStatus.EM_ANALISE,
            EvidenceStatus.ARQUIVADO, EvidenceStatus.HASH_DIVERGENTE, EvidenceStatus.ERRO);
    private final EvidenceRepository repository;

    public EvidenceRemovalService(EvidenceRepository repository) {
        this.repository = repository;
    }

    public static boolean canRemove(EvidenceStatus status) {
        return status != null && REMOVABLE.contains(status);
    }

    @Transactional
    public void remove(Long id, String identifier, boolean confirmed) {
        if (!confirmed || id == null || identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Confirme a remoção do registro com seu identificador.");
        }
        // The predicate and deletion run in one statement, competing with the operational claims.
        // No filesystem operation or entity cascade is involved.
        if (repository.deleteRemovable(id, identifier, REMOVABLE) != 1) {
            throw new IllegalStateException("Registro não removido: operação em curso, "
                    + "identificador diferente ou registro já removido. Consulte a listagem.");
        }
    }
}
