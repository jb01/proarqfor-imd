package com.forenstorage.web.controller;

import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ArchivingWebIntegrationTest {
    @TempDir static Path directory;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve("web.db"));
        properties.add("forenstorage.storage.fast", () -> directory.resolve("fast").toString());
        properties.add("forenstorage.storage.work", () -> directory.resolve("work").toString());
        properties.add("forenstorage.storage.archive", () -> directory.resolve("archive").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired EvidenceRepository repository;

    @Test
    void registrationArchiveAndDetailsUseRealServicesAndTemporarySqlite() throws Exception {
        Path fast = Files.createDirectories(directory.resolve("fast"));
        Path original = Files.writeString(fast.resolve("synthetic.dd"), "abc");
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        mvc.perform(post("/evidences").param("evidenceIdentifier", "web/2026")
                        .param("currentPath", original.toString()).param("informedHash", hash))
                .andExpect(redirectedUrl("/evidences"));
        Long id = repository.findByEvidenceIdentifier("web/2026").orElseThrow().getId();
        String details = "/evidences/" + id;
        mvc.perform(get(details)).andExpect(model().attribute("canArchive", true))
                .andExpect(content().string(containsString("Ainda não gerada.")));

        var response = mvc.perform(post(details + "/archive"))
                .andExpect(redirectedUrl(details)).andExpect(flash().attributeExists("success")).andReturn();

        var saved = repository.findById(id).orElseThrow();
        assertEquals(EvidenceStatus.ARQUIVADO, saved.getStatus());
        assertFalse(Files.exists(original));
        assertTrue(Files.isRegularFile(Path.of(saved.getArchivedPath())));
        assertEquals(hash, saved.getCalculatedHash());
        assertNotNull(saved.getEncryptionPassword());
        // Do not put actual generated secrets into matcher diagnostics on failure.
        String html = mvc.perform(get(details).flashAttrs(response.getFlashMap()))
                .andExpect(status().isOk()).andExpect(model().attribute("canArchive", false))
                .andExpect(content().string(containsString("Evidência arquivada com sucesso.")))
                .andExpect(content().string(containsString("ARQUIVADO")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(saved.getEncryptionPassword()), "A senha persistida deve aparecer nos detalhes");
        assertFalse(html.contains(saved.getEncryptionKey()), "A chave não deve aparecer no HTML");
        assertTrue(html.contains(saved.getArchivedPath()));
        try (var files = Files.walk(directory.resolve("work"))) {
            var artifacts = files.filter(Files::isRegularFile).toList();
            assertEquals(1, artifacts.size());
            assertEquals("abc", Files.readString(artifacts.getFirst()));
        }

        mvc.perform(post(details + "/archive")).andExpect(flash().attributeExists("error"));
        assertEquals(EvidenceStatus.ARQUIVADO, repository.findById(id).orElseThrow().getStatus());
        assertTrue(Files.isRegularFile(Path.of(saved.getArchivedPath())));
    }
}
