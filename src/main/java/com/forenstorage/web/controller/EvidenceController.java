package com.forenstorage.web.controller;

import com.forenstorage.web.model.Evidence;
import com.forenstorage.web.model.EvidenceStatus;
import com.forenstorage.web.repository.EvidenceRepository;
import com.forenstorage.web.service.EvidenceRegistrationService;
import com.forenstorage.web.service.ArchivingService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;

@Controller
@RequestMapping("/evidences")
public class EvidenceController {
    private final EvidenceRepository repository;
    private final EvidenceRegistrationService registration;
    private final ArchivingService archiving;

    public EvidenceController(EvidenceRepository repository, EvidenceRegistrationService registration,
                              ArchivingService archiving) {
        this.repository = repository;
        this.registration = registration;
        this.archiving = archiving;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("evidences", repository.findAll());
        return "evidences/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        formValues(model, "", "", "");
        return "evidences/form";
    }

    @PostMapping
    public String register(@RequestParam(defaultValue = "") String evidenceIdentifier,
                           @RequestParam(defaultValue = "") String currentPath,
                           @RequestParam(defaultValue = "") String informedHash,
                           @RequestParam(defaultValue = "EM_ANALISE") String status,
                           Model model, RedirectAttributes redirect) {
        formValues(model, evidenceIdentifier, currentPath, informedHash);
        try {
            if (!"EM_ANALISE".equals(status)) {
                throw new IllegalArgumentException("O status inicial é determinado pela verificação do hash.");
            }
            if (currentPath.isBlank()) {
                throw new IllegalArgumentException("Path atual é obrigatório");
            }
            Evidence saved = registration.register(evidenceIdentifier, Path.of(currentPath), informedHash);
            if (saved.getStatus() == EvidenceStatus.HASH_DIVERGENTE) {
                redirect.addFlashAttribute("warning", "Evidência cadastrada com HASH_DIVERGENTE. "
                        + "Confira os hashes nos detalhes. Remova a evidência e cadastre novamente.");
            } else {
                redirect.addFlashAttribute("success", "Evidência cadastrada com sucesso.");
            }
            return "redirect:/evidences";
        } catch (IllegalArgumentException | IOException e) {
            model.addAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            model.addAttribute("error", "Não foi possível salvar a evidência. Tente novamente.");
        }
        return "evidences/form";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        try {
            Evidence evidence = findEvidence(id);
            model.addAttribute("evidence", evidence);
            model.addAttribute("canArchive", canArchive(evidence));
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível consultar a evidência; o estado atual não pôde ser confirmado.");
        }
        return "evidences/details";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, @RequestParam(required = false) String status,
                          RedirectAttributes redirect) {
        try {
            Evidence evidence = findEvidence(id);
            if (status != null) {
                throw new IllegalArgumentException("A ação Arquivar não aceita status manual.");
            }
            if (!canArchive(evidence)) {
                throw new IllegalStateException("Arquivamento indisponível para esta evidência. "
                        + "É necessário EM_ANALISE com .dd original e sem artefato já arquivado.");
            }
            // Only the persisted id enters the use case; paths, hashes and status are not bound from the form.
            // The service revalidates eligibility and owns all retries, file operations and transitions.
            archiving.archive(id);
            redirect.addFlashAttribute("success", "Evidência arquivada com sucesso.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "Falha de persistência: não foi possível confirmar o arquivamento. "
                    + "Consulte o estado atual da evidência.");
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/evidences/" + id;
    }

    private Evidence findEvidence(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidência não encontrada"));
    }

    private boolean canArchive(Evidence evidence) {
        return evidence.getStatus() == EvidenceStatus.EM_ANALISE && evidence.getArchivedPath() == null
                && evidence.getCurrentPath().endsWith(".dd");
    }

    private void formValues(Model model, String identifier, String path, String hash) {
        model.addAttribute("evidenceIdentifier", identifier);
        model.addAttribute("currentPath", path);
        model.addAttribute("informedHash", hash);
    }
}
