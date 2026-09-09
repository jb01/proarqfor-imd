package com.forenstorage.web.controller;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import com.forenstorage.web.service.EvidenceRegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    private static final String HASH = "a".repeat(64);
    private static final String PATH = "storage/fast/test.dd";

    private Evidence evidence(EvidenceStatus status) {
        Evidence evidence = new Evidence("0001/2026", PATH, HASH, "b".repeat(64), status);
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
                .andExpect(content().string(matchesPattern("(?s).*id=\"status-button\"[^>]*disabled[^>]*>.*")))
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
    void craftedPostCannotArchiveOrChangeStatus() throws Exception {
        mvc.perform(post("/evidences/7/archive")).andExpect(status().isNotFound());
        mvc.perform(post("/evidences/7/status").param("status", "EM_ANALISE")).andExpect(status().isNotFound());
        verifyNoInteractions(registration, repository);
    }
}
