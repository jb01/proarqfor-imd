package com.forenstorage.web.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static com.forenstorage.web.model.EvidenceStatus.*;

class EvidenceTest {
    private static final Set<String> ALLOWED = Set.of(
            "EM_ANALISE:ARQUIVANDO", "ARQUIVANDO:ARQUIVADO",
            "ARQUIVADO:DESARQUIVANDO", "DESARQUIVANDO:EM_ANALISE",
            "EM_ANALISE:ERRO", "ARQUIVANDO:ERRO", "ARQUIVADO:ERRO", "DESARQUIVANDO:ERRO");

    static Stream<Arguments> transitions() {
        return Arrays.stream(values()).flatMap(from -> Arrays.stream(values())
                .map(to -> Arguments.of(from, to, ALLOWED.contains(from + ":" + to))));
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("transitions")
    void enforcesEntireTransitionMatrix(EvidenceStatus from, EvidenceStatus to, boolean allowed) {
        Evidence evidence = at(from);
        assertEquals(allowed, from.canTransitionTo(to));
        if (allowed) {
            evidence.transitionTo(to);
            assertEquals(to, evidence.getStatus());
        } else {
            assertThrows(IllegalStateException.class, () -> evidence.transitionTo(to));
            assertEquals(from, evidence.getStatus());
        }
    }

    @ParameterizedTest
    @EnumSource(EvidenceStatus.class)
    void rejectsNullTargetWithoutChangingState(EvidenceStatus from) {
        Evidence evidence = at(from);
        assertFalse(from.canTransitionTo(null));
        assertThrows(IllegalStateException.class, () -> evidence.transitionTo(null));
        assertEquals(from, evidence.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"HASH_DIVERGENTE", "ERRO"})
    void blockedStatesCannotBeMarkedAsErrorAgain(EvidenceStatus from) {
        Evidence evidence = at(from);
        assertThrows(IllegalStateException.class, () -> evidence.markError("falha"));
        assertEquals(from, evidence.getStatus());
        assertNull(evidence.getErrorMessage());
    }

    @Test
    void recordsErrorAndRejectsBlankMessageWithoutMutation() {
        Evidence evidence = at(EM_ANALISE);
        assertThrows(IllegalArgumentException.class, () -> evidence.markError(" "));
        assertEquals(EM_ANALISE, evidence.getStatus());
        evidence.markError("Falha operacional");
        assertEquals(ERRO, evidence.getStatus());
        assertEquals("Falha operacional", evidence.getErrorMessage());
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"ARQUIVANDO", "ARQUIVADO", "DESARQUIVANDO", "ERRO"})
    void rejectsOperationalStateAsInitialState(EvidenceStatus initial) {
        assertThrows(IllegalArgumentException.class, () -> create(initial));
    }

    static Evidence create(EvidenceStatus initial) {
        return new Evidence("0001/2026", "storage/fast/example.dd", "a".repeat(64), "b".repeat(64), initial);
    }

    static Evidence at(EvidenceStatus status) {
        Evidence e = create(status == HASH_DIVERGENTE ? HASH_DIVERGENTE : EM_ANALISE);
        switch (status) {
            case ARQUIVANDO -> e.transitionTo(ARQUIVANDO);
            case ARQUIVADO -> { e.transitionTo(ARQUIVANDO); e.transitionTo(ARQUIVADO); }
            case DESARQUIVANDO -> {
                e.transitionTo(ARQUIVANDO); e.transitionTo(ARQUIVADO); e.transitionTo(DESARQUIVANDO);
            }
            case ERRO -> e.transitionTo(ERRO);
            default -> { }
        }
        return e;
    }
}
