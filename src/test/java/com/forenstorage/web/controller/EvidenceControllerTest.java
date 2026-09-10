package com.forenstorage.web.controller;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import com.forenstorage.web.service.EvidenceRegistrationService;
import com.forenstorage.web.service.ArchivingService;
import com.forenstorage.web.service.UnarchivingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvidenceController.class)
class EvidenceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean EvidenceRepository repository;
    @MockitoBean EvidenceRegistrationService registration;
    @MockitoBean ArchivingService archiving;
    @MockitoBean UnarchivingService unarchiving;
    @MockitoBean com.forenstorage.web.service.EvidenceRemovalService removal;
    private Evidence archivedEvidence() {
        Evidence e = evidence(EvidenceStatus.ARQUIVADO);
        e.setCurrentPath("storage/cold/archive/7/test.zip.enc");
        e.setArchivedPath(e.getCurrentPath());
        return e;
    }

    @Test
    void unarchiveFormUsesPostAndGetDoesNotExecute() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(archivedEvidence()));
        mvc.perform(get("/evidences/7")).andExpect(model().attribute("canUnarchive", true))
                .andExpect(content().string(containsString("action=\"/evidences/7/unarchive\"")))
                .andExpect(content().string(not(matchesPattern("(?s).*id=\"unarchive-button\"[^>]*disabled.*"))))
                .andExpect(content().string(containsString("Não restaura o .dd")))
                .andExpect(content().string(not(containsString("Alterar status"))));
        mvc.perform(get("/evidences/7/unarchive")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(unarchiving);
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = "ARQUIVADO", mode = EnumSource.Mode.EXCLUDE)
    void unarchiveBlocksAllOtherStatesInViewAndPost(EvidenceStatus state) throws Exception {
        Evidence e = archivedEvidence();
        ReflectionTestUtils.setField(e, "status", state);
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(model().attribute("canUnarchive", false))
                .andExpect(content().string(matchesPattern("(?s).*id=\"unarchive-button\"[^>]*disabled.*")));
        mvc.perform(post("/evidences/7/unarchive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attributeExists("error")).andExpect(flash().attributeCount(1));
        verifyNoInteractions(unarchiving);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "different", "extension"})
    void unarchiveRejectsInconsistentArtifact(String kind) throws Exception {
        Evidence e = archivedEvidence();
        if (kind.equals("missing")) e.setArchivedPath(null);
        else if (kind.equals("different")) e.setArchivedPath("another.zip.enc");
        else { e.setCurrentPath(PATH); e.setArchivedPath(PATH); }
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(model().attribute("canUnarchive", false));
        mvc.perform(post("/evidences/7/unarchive")).andExpect(flash().attributeExists("error"));
        verifyNoInteractions(unarchiving);
    }

    @Test
    void unarchiveUsesOnlyIdAndRedirectsToFreshDetails() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(archivedEvidence()));
        var result = mvc.perform(post("/evidences/7/unarchive").param("currentPath", "untrusted.dd")
                        .param("encryptionPassword", "untrusted").param("informedHash", "untrusted"))
                .andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attribute("success", containsString("continua cifrado")))
                .andExpect(flash().attributeCount(1)).andReturn();
        Evidence returned = archivedEvidence();
        ReflectionTestUtils.setField(returned, "status", EvidenceStatus.EM_ANALISE);
        returned.setCurrentPath("storage/fast/7/test.zip.enc");
        when(repository.findById(7L)).thenReturn(Optional.of(returned));
        mvc.perform(get("/evidences/7").flashAttrs(result.getFlashMap()))
                .andExpect(model().attribute("canArchive", false)).andExpect(model().attribute("canUnarchive", false))
                .andExpect(content().string(containsString("Retorno simulado:")))
                .andExpect(content().string(containsString(returned.getCurrentPath())));
        verify(unarchiving, times(1)).unarchive(7L);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(archiving, registration);
    }

    @Test
    void unarchiveRejectsManualStatusAndUnknownId() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(archivedEvidence()));
        mvc.perform(post("/evidences/7/unarchive").param("status", "EM_ANALISE"))
                .andExpect(flash().attribute("error", containsString("não aceita status manual")));
        mvc.perform(post("/evidences/99/unarchive")).andExpect(status().isNotFound());
        verifyNoInteractions(unarchiving);
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "concurrent", "invalid", "sqlite"})
    void unarchiveFailuresDoNotRetryOrClaimSuccess(String kind) throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(archivedEvidence()));
        Exception failure = switch (kind) {
            case "io" -> new IOException("não foi possível confirmar ERRO");
            case "concurrent" -> new IllegalStateException("Desarquivamento já iniciado");
            case "invalid" -> new IllegalArgumentException("Arquivo inválido");
            default -> new org.springframework.dao.DataAccessResourceFailureException("internal SQL");
        };
        when(unarchiving.unarchive(7L)).thenThrow(failure);
        mvc.perform(post("/evidences/7/unarchive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attributeExists("error")).andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("error", not(containsString("internal SQL"))));
        verify(unarchiving, times(1)).unarchive(7L);
    }

    @Test
    void unarchiveLookupFailureIsSanitized() throws Exception {
        when(repository.findById(7L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal SQL"));
        mvc.perform(post("/evidences/7/unarchive"))
                .andExpect(flash().attribute("error", containsString("não foi possível confirmar o desarquivamento")))
                .andExpect(flash().attribute("error", not(containsString("internal SQL"))));
        mvc.perform(get("/evidences/7")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(unarchiving);
    }

    private static final String HASH = "a".repeat(64);
    private static final String PATH = "storage/fast/test.dd";

    private Evidence evidence(EvidenceStatus status) {
        Evidence evidence = new Evidence("0001/2026", PATH, HASH, "b".repeat(64),
                status == EvidenceStatus.HASH_DIVERGENTE ? status : EvidenceStatus.EM_ANALISE);
        ReflectionTestUtils.setField(evidence, "status", status);
        ReflectionTestUtils.setField(evidence, "id", 7L);
        ReflectionTestUtils.setField(evidence, "createdAt", Instant.parse("2026-09-08T12:00:00Z"));
        return evidence;
    }

    @Test
    void emptyListOffersRegistration() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get("/evidences")).andExpect(status().isOk()).andExpect(view().name("evidences/list"))
                .andExpect(content().string(containsString("Nenhuma evidência cadastrada")))
                .andExpect(content().string(containsString("href=\"/evidences/new\"")));
    }

    @Test
    void listsEvidenceWithLinkToDetails() throws Exception {
        when(repository.findAll()).thenReturn(List.of(evidence(EvidenceStatus.EM_ANALISE)));
        mvc.perform(get("/evidences")).andExpect(status().isOk())
                .andExpect(content().string(containsString("0001/2026")))
                .andExpect(content().string(containsString("EM_ANALISE")))
                .andExpect(content().string(containsString("href=\"/evidences/7\"")));
    }

    @Test
    void formContainsFieldsAndCancelDoesNotRegister() throws Exception {
        mvc.perform(get("/evidences/new")).andExpect(status().isOk()).andExpect(view().name("evidences/form"))
                .andExpect(content().string(containsString("name=\"evidenceIdentifier\"")))
                .andExpect(content().string(containsString("name=\"currentPath\"")))
                .andExpect(content().string(containsString("name=\"informedHash\"")))
                .andExpect(content().string(containsString("href=\"/evidences\">Cancelar</a>")));
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get("/evidences")).andExpect(status().isOk());
        verifyNoInteractions(registration);
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"EM_ANALISE", "HASH_DIVERGENTE"})
    void successfulRegistrationRedirectsToList(EvidenceStatus initial) throws Exception {
        when(registration.register("0001/2026", Path.of(PATH), HASH)).thenReturn(evidence(initial));
        var result = mvc.perform(post("/evidences").param("evidenceIdentifier", "0001/2026")
                        .param("currentPath", PATH).param("informedHash", HASH).param("status", "EM_ANALISE"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/evidences"));
        if (initial == EvidenceStatus.HASH_DIVERGENTE) {
            result.andExpect(flash().attribute("warning", containsString("Remova a evidência e cadastre novamente")));
        } else {
            result.andExpect(flash().attributeExists("success"));
        }
        verify(registration).register("0001/2026", Path.of(PATH), HASH);
    }

    @Test
    void duplicatePreservesFormAndDisplaysError() throws Exception {
        when(registration.register("0001/2026", Path.of(PATH), HASH))
                .thenThrow(new IllegalArgumentException("Identificador de evidência já cadastrado: 0001/2026"));
        mvc.perform(post("/evidences").param("evidenceIdentifier", "0001/2026")
                        .param("currentPath", PATH).param("informedHash", HASH))
                .andExpect(status().isOk()).andExpect(view().name("evidences/form"))
                .andExpect(model().attribute("currentPath", PATH))
                .andExpect(model().attribute("informedHash", HASH))
                .andExpect(content().string(containsString("já cadastrado")))
                .andExpect(content().string(containsString("value=\"0001/2026\"")));
    }

    @Test
    void missingFileShowsReadableError() throws Exception {
        when(registration.register("0001/2026", Path.of(PATH), HASH)).thenThrow(new IOException("Arquivo não encontrado"));
        mvc.perform(post("/evidences").param("evidenceIdentifier", "0001/2026")
                        .param("currentPath", PATH).param("informedHash", HASH))
                .andExpect(status().isOk()).andExpect(view().name("evidences/form"))
                .andExpect(content().string(containsString("Arquivo não encontrado")));
    }

    @Test
    void databaseErrorDoesNotExposeInternalDetails() throws Exception {
        when(registration.register("0001/2026", Path.of(PATH), HASH))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal SQL details"));
        mvc.perform(post("/evidences").param("evidenceIdentifier", "0001/2026")
                        .param("currentPath", PATH).param("informedHash", HASH))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Não foi possível salvar")))
                .andExpect(content().string(not(containsString("internal SQL details"))));
    }

    @Test
    void rejectsForgedStatusAndBlankPathWithoutCallingUseCase() throws Exception {
        mvc.perform(post("/evidences").param("status", "ARQUIVADO").param("currentPath", PATH))
                .andExpect(view().name("evidences/form")).andExpect(model().attributeExists("error"));
        mvc.perform(post("/evidences").param("currentPath", " "))
                .andExpect(view().name("evidences/form")).andExpect(model().attributeExists("error"));
        verifyNoInteractions(registration);
    }

    @Test
    void detailsShowHashesAndVisibleDivergenceWithDisabledControls() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.HASH_DIVERGENTE)));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk()).andExpect(view().name("evidences/details"))
                .andExpect(content().string(containsString("0001/2026")))
                .andExpect(content().string(containsString(PATH)))
                .andExpect(content().string(containsString(HASH)))
                .andExpect(content().string(containsString("b".repeat(64))))
                .andExpect(content().string(containsString("2026-09-08T12:00:00Z")))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("Remova a evidência e cadastre novamente")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"archive-button\"[^>]*disabled[^>]*>.*")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"unarchive-button\"[^>]*disabled[^>]*>.*")))
                .andExpect(content().string(not(containsString("<dt>Path arquivado</dt>"))));
    }

    @Test
    void detailsShowOptionalArchiveAndEscapedError() throws Exception {
        Evidence e = evidence(EvidenceStatus.EM_ANALISE);
        e.setArchivedPath("storage/cold/archive/test.zip.enc");
        e.markError("<script>alert('erro')</script>");
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk())
                .andExpect(content().string(containsString("storage/cold/archive/test.zip.enc")))
                .andExpect(content().string(containsString("Mensagem de erro")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("ERRO: arquivamento e mudanças de status estão bloqueados")));
    }

    @Test
    void missingEvidenceReturns404() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/evidences/99")).andExpect(status().isNotFound());
    }

    @Test
    void manualStatusEndpointRemainsUnavailable() throws Exception {
        mvc.perform(post("/evidences/7/status").param("status", "EM_ANALISE")).andExpect(status().isNotFound());
        verifyNoInteractions(registration, repository, archiving);
    }

    @Test
    void eligibleDetailsOfferPostFormWithoutTriggeringArchiving() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk())
                .andExpect(model().attribute("canArchive", true))
                .andExpect(content().string(containsString("method=\"post\"")))
                .andExpect(content().string(containsString("action=\"/evidences/7/archive\"")))
                .andExpect(content().string(not(matchesPattern("(?s).*id=\"archive-button\"[^>]*disabled.*"))));
        mvc.perform(get("/evidences/7/archive")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(archiving);
    }

    @Test
    void archivePostCallsUseCaseOnceAndRedirectsToFreshDetails() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        when(archiving.archive(7L)).thenReturn(evidence(EvidenceStatus.ARQUIVADO));
        var result = mvc.perform(post("/evidences/7/archive").param("currentPath", "outside.dd")
                        .param("encryptionPassword", "untrusted"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attribute("success", "Evidência arquivada com sucesso."))
                .andExpect(flash().attributeCount(1)).andReturn();
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.ARQUIVADO)));
        mvc.perform(get("/evidences/7").flashAttrs(result.getFlashMap())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Evidência arquivada com sucesso.")))
                .andExpect(content().string(containsString("ARQUIVADO")))
                .andExpect(model().attribute("canArchive", false));
        verify(archiving, times(1)).archive(7L);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(registration);
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = "EM_ANALISE", mode = EnumSource.Mode.EXCLUDE)
    void ineligibleStateDisablesButtonAndRejectsForgedPost(EvidenceStatus state) throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(state)));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk())
                .andExpect(model().attribute("canArchive", false))
                .andExpect(content().string(matchesPattern("(?s).*id=\"archive-button\"[^>]*disabled.*")));
        mvc.perform(post("/evidences/7/archive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attributeExists("error")).andExpect(flash().attributeCount(1));
        verifyNoInteractions(archiving);
    }

    @Test
    void rejectsReturnedCiphertextAndAlreadyArchivedMetadata() throws Exception {
        Evidence e = evidence(EvidenceStatus.EM_ANALISE);
        e.setCurrentPath("storage/fast/test.zip.enc");
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(model().attribute("canArchive", false));
        mvc.perform(post("/evidences/7/archive")).andExpect(flash().attributeExists("error"));
        e.setCurrentPath(PATH);
        e.setArchivedPath("storage/cold/archive/test.zip.enc");
        mvc.perform(get("/evidences/7")).andExpect(model().attribute("canArchive", false));
        mvc.perform(post("/evidences/7/archive")).andExpect(flash().attributeExists("error"));
        verifyNoInteractions(archiving);
    }

    @Test
    void archivePostCannotSupplyStatus() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        mvc.perform(post("/evidences/7/archive").param("status", "ARQUIVADO"))
                .andExpect(flash().attribute("error", containsString("não aceita status manual")));
        verifyNoInteractions(archiving);
    }

    @Test
    void archiveUnknownEvidenceReturns404WithoutCallingService() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(post("/evidences/99/archive")).andExpect(status().isNotFound());
        verifyNoInteractions(archiving);
    }

    @Test
    void operationalFailureShowsFreshPersistedErrorAndDoesNotRetryInController() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        when(archiving.archive(7L)).thenThrow(new IOException("Arquivamento falhou após duas tentativas"));
        var result = mvc.perform(post("/evidences/7/archive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attribute("error", containsString("duas tentativas")))
                .andExpect(flash().attributeCount(1)).andReturn();
        Evidence failed = evidence(EvidenceStatus.EM_ANALISE);
        failed.markError("Falha operacional persistida");
        when(repository.findById(7L)).thenReturn(Optional.of(failed));
        mvc.perform(get("/evidences/7").flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("Falha operacional persistida")))
                .andExpect(content().string(containsString("Arquivamento falhou após duas tentativas")))
                .andExpect(model().attribute("canArchive", false));
        verify(archiving, times(1)).archive(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Arquivo inexistente", "Não foi possível confirmar ERRO"})
    void ioFailuresAreVisibleWithoutClaimingSuccess(String message) throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        when(archiving.archive(7L)).thenThrow(new IOException(message));
        mvc.perform(post("/evidences/7/archive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attribute("error", message)).andExpect(flash().attributeCount(1));
        verify(archiving, times(1)).archive(7L);
    }

    @Test
    void serviceStillRejectsStateChangedAfterControllerCheck() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        when(archiving.archive(7L)).thenThrow(new IllegalStateException("Arquivamento já em andamento"));
        mvc.perform(post("/evidences/7/archive")).andExpect(redirectedUrl("/evidences/7"))
                .andExpect(flash().attribute("error", "Arquivamento já em andamento"))
                .andExpect(flash().attributeCount(1));
        verify(archiving, times(1)).archive(7L);
    }

    @Test
    void databaseFailureIsSanitizedAndUnavailableDetailsReturn503() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(evidence(EvidenceStatus.EM_ANALISE)));
        when(archiving.archive(7L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal SQL"));
        mvc.perform(post("/evidences/7/archive")).andExpect(flash().attribute("error", containsString("Falha de persistência")))
                .andExpect(flash().attribute("error", not(containsString("internal SQL"))))
                .andExpect(flash().attributeCount(1));
        when(repository.findById(7L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal SQL"));
        mvc.perform(post("/evidences/7/archive")).andExpect(flash().attribute("error", containsString("Falha de persistência")));
        mvc.perform(get("/evidences/7")).andExpect(status().isServiceUnavailable());
        verify(archiving, times(1)).archive(7L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void missingPasswordKeepsPlaceholder(String password) throws Exception {
        Evidence e = evidence(EvidenceStatus.EM_ANALISE);
        ReflectionTestUtils.setField(e, "encryptionPassword", password);
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ainda não gerada.")));
    }

    @Test
    void persistedPasswordIsEscapedAndKeyIsNeverRendered() throws Exception {
        Evidence e = evidence(EvidenceStatus.ARQUIVADO);
        ReflectionTestUtils.setField(e, "encryptionPassword", "<senha>&");
        ReflectionTestUtils.setField(e, "encryptionKey", "INTERNAL_KEY_SENTINEL");
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        mvc.perform(get("/evidences/7")).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;senha&gt;&amp;")))
                .andExpect(content().string(not(containsString("<senha>"))))
                .andExpect(content().string(not(containsString("Ainda não gerada."))))
                .andExpect(content().string(not(containsString("INTERNAL_KEY_SENTINEL"))));
    }
}
