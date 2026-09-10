package com.forenstorage.web.controller;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import com.forenstorage.web.service.EvidenceRemovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EvidenceRemovalIntegrationTest {
    @TempDir static Path directory;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("forenstorage.data-root", () -> directory.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired EvidenceRepository repository;
    @Autowired EvidenceRemovalService removal;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest
    @EnumSource(EvidenceStatus.class)
    void confirmationAndPostRespectEveryStateAndPreserveAllFiles(EvidenceStatus state) throws Exception {
        Evidence e = create(state);
        String url = "/evidences/" + e.getId();
        boolean allowed = state != EvidenceStatus.ARQUIVANDO && state != EvidenceStatus.DESARQUIVANDO;
        mvc.perform(get(url)).andExpect(model().attribute("canRemove", allowed));
        mvc.perform(get(url + "/remove")).andExpect(status().isOk())
                .andExpect(model().attribute("canRemove", allowed))
                .andExpect(content().string(containsString(e.getEvidenceIdentifier())))
                .andExpect(content().string(containsString("Tem certeza que deseja deletar a evidência")))
                .andExpect(content().string(containsString("href=\"" + url + "\"")));
        assertTrue(repository.existsById(e.getId()), "GET de confirmação não remove");
        mvc.perform(get(url)).andExpect(status().isOk()); // Follow the Cancelar link.
        assertTrue(repository.existsById(e.getId()), "Cancelar mantém o cadastro");
        mvc.perform(post(url + "/remove").param("confirmed", "true")
                        .param("evidenceIdentifier", e.getEvidenceIdentifier())
                        .param("currentPath", "/ignored").param("status", "EM_ANALISE"))
                .andExpect(redirectedUrl(allowed ? "/evidences" : url))
                .andExpect(flash().attributeExists(allowed ? "success" : "error"));
        assertEquals(!allowed, repository.existsById(e.getId()));
        if (allowed) {
            mvc.perform(get("/evidences")).andExpect(content().string(not(containsString(e.getEvidenceIdentifier()))));
            mvc.perform(get(url)).andExpect(status().isNotFound());
        } else {
            assertEquals(state, repository.findById(e.getId()).orElseThrow().getStatus());
        }
        assertFiles(e);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "false", "identifier", "other-record"})
    void invalidConfirmationNeverRemoves(String kind) throws Exception {
        Evidence e = create(EvidenceStatus.EM_ANALISE);
        var request = post("/evidences/" + e.getId() + "/remove");
        if (!kind.equals("missing")) request.param("confirmed", kind.equals("false") ? "false" : "true");
        request.param("evidenceIdentifier", kind.equals("identifier") ? "wrong" :
                kind.equals("other-record") ? create(EvidenceStatus.EM_ANALISE).getEvidenceIdentifier() : e.getEvidenceIdentifier());
        mvc.perform(request).andExpect(flash().attributeExists("error"));
        assertTrue(repository.existsById(e.getId()));
        assertFiles(e);
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"EM_ANALISE", "ARQUIVADO"})
    void operationalClaimAfterConfirmationBlocksDeletionIncludingDirectServiceCall(EvidenceStatus state) throws Exception {
        Evidence e = create(state);
        mvc.perform(get("/evidences/" + e.getId() + "/remove")).andExpect(model().attribute("canRemove", true));
        assertEquals(1, claim(e));
        assertThrows(IllegalStateException.class, () -> removal.remove(e.getId(), e.getEvidenceIdentifier(), true));
        mvc.perform(post("/evidences/" + e.getId() + "/remove").param("confirmed", "true")
                .param("evidenceIdentifier", e.getEvidenceIdentifier())).andExpect(flash().attributeExists("error"));
        assertEquals(state == EvidenceStatus.EM_ANALISE ? EvidenceStatus.ARQUIVANDO : EvidenceStatus.DESARQUIVANDO,
                repository.findById(e.getId()).orElseThrow().getStatus());
        assertFiles(e);
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"EM_ANALISE", "ARQUIVADO"})
    void deletionBeforeOperationalClaimPreventsOperationFromStarting(EvidenceStatus state) throws Exception {
        Evidence stale = create(state);
        removal.remove(stale.getId(), stale.getEvidenceIdentifier(), true);
        assertEquals(0, claim(stale));
        assertFalse(repository.existsById(stale.getId()));
        assertFiles(stale);
    }

    @Test
    void sqliteRejectionRollsBackAndShowsSanitizedError() throws Exception {
        Evidence e = create(EvidenceStatus.EM_ANALISE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("data/arqfor.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_removal BEFORE DELETE ON evidence WHEN OLD.id = " + e.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'internal-removal-detail'); END");
            try {
                mvc.perform(post("/evidences/" + e.getId() + "/remove").param("confirmed", "true")
                                .param("evidenceIdentifier", e.getEvidenceIdentifier()))
                        .andExpect(flash().attribute("error", "Não foi possível confirmar a remoção do registro. Consulte a listagem."))
                        .andExpect(flash().attributeCount(1));
                assertTrue(repository.existsById(e.getId()));
                assertFiles(e);
            } finally {
                statement.execute("DROP TRIGGER reject_removal");
            }
        }
    }

    @Test
    void missingAndRepeatedRemovalReturn404() throws Exception {
        Evidence e = create(EvidenceStatus.EM_ANALISE);
        removal.remove(e.getId(), e.getEvidenceIdentifier(), true);
        mvc.perform(get("/evidences/" + e.getId() + "/remove")).andExpect(status().isNotFound());
        mvc.perform(post("/evidences/" + e.getId() + "/remove").param("confirmed", "true")
                .param("evidenceIdentifier", e.getEvidenceIdentifier())).andExpect(status().isNotFound());
        assertFiles(e);
    }

    @Test
    void identifierIsEscapedInConfirmation() throws Exception {
        Evidence e = repository.saveAndFlush(new Evidence("<script>alert('x')</script>", "unused.dd", "a", "a", EvidenceStatus.EM_ANALISE));
        mvc.perform(get("/evidences/" + e.getId() + "/remove"))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    private int claim(Evidence e) {
        return new TransactionTemplate(manager).execute(tx -> e.getStatus() == EvidenceStatus.EM_ANALISE
                ? repository.claimArchiving(e.getId(), e.getCurrentPath(), EvidenceStatus.EM_ANALISE, EvidenceStatus.ARQUIVANDO)
                : repository.claimUnarchiving(e.getId(), e.getCurrentPath(), EvidenceStatus.ARQUIVADO, EvidenceStatus.DESARQUIVANDO));
    }

    private Evidence create(EvidenceStatus state) throws Exception {
        String name = UUID.randomUUID().toString();
        Path fast = Files.createDirectories(directory.resolve("storage/fast")).resolve(name + ".dd");
        Path work = Files.createDirectories(directory.resolve("storage/cold/work")).resolve(name + ".dd");
        Path archive = Files.createDirectories(directory.resolve("storage/cold/archive")).resolve(name + ".zip.enc");
        Files.writeString(fast, "synthetic-fast");
        Files.writeString(work, "synthetic-work");
        Files.writeString(archive, "synthetic-ciphertext");
        Evidence e = new Evidence(name, fast.toString(), "a".repeat(64), "a".repeat(64),
                state == EvidenceStatus.HASH_DIVERGENTE ? state : EvidenceStatus.EM_ANALISE);
        if (state == EvidenceStatus.ERRO) e.markError("Falha sintética");
        if (state == EvidenceStatus.ARQUIVANDO || state == EvidenceStatus.ARQUIVADO || state == EvidenceStatus.DESARQUIVANDO)
            e.transitionTo(EvidenceStatus.ARQUIVANDO);
        if (state == EvidenceStatus.ARQUIVADO || state == EvidenceStatus.DESARQUIVANDO) {
            e.recordEncryption(archive.toString(), "synthetic-iv", "synthetic-password", "synthetic-key", "synthetic-salt", 600000, 1);
            e.transitionTo(EvidenceStatus.ARQUIVADO);
        }
        if (state == EvidenceStatus.DESARQUIVANDO) e.transitionTo(state);
        return repository.saveAndFlush(e);
    }

    private void assertFiles(Evidence e) throws Exception {
        String name = e.getEvidenceIdentifier();
        assertEquals("synthetic-fast", Files.readString(directory.resolve("storage/fast/" + name + ".dd")));
        assertEquals("synthetic-work", Files.readString(directory.resolve("storage/cold/work/" + name + ".dd")));
        assertEquals("synthetic-ciphertext", Files.readString(directory.resolve("storage/cold/archive/" + name + ".zip.enc")));
    }
}
