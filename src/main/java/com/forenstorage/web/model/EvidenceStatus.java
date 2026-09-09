package com.forenstorage.web.model;

public enum EvidenceStatus {
    EM_ANALISE, ARQUIVANDO, ARQUIVADO, DESARQUIVANDO, HASH_DIVERGENTE, ERRO;

    public boolean canTransitionTo(EvidenceStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case EM_ANALISE -> target == ARQUIVANDO || target == ERRO;
            case ARQUIVANDO -> target == ARQUIVADO || target == ERRO;
            case ARQUIVADO -> target == DESARQUIVANDO || target == ERRO;
            case DESARQUIVANDO -> target == EM_ANALISE || target == ERRO;
            case HASH_DIVERGENTE, ERRO -> false;
        };
    }
}
