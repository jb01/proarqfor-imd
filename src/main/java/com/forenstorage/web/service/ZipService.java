package com.forenstorage.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipService {
    private final Path workStorage;

    public ZipService(@Value("${forenstorage.storage.work:storage/cold/work}") String workStorage) {
        this.workStorage = Path.of(workStorage).toAbsolutePath().normalize();
    }

    /**
     * Compacta a cópia de trabalho em um ZIP novo no mesmo diretório.
     * Retorna somente após fechamento bem-sucedido. Em falha, preserva a origem
     * e qualquer ZIP parcial; sua existência não indica conclusão da etapa.
     */
    public Path compress(Path source) throws IOException {
        return compress(source, source.resolveSibling(source.getFileName() + ".zip"));
    }

    // The orchestrator supplies a fresh sibling for each attempt, preserving partial ZIPs.
    Path compress(Path source, Path destination) throws IOException {
        Objects.requireNonNull(source, "O caminho do arquivo é obrigatório");
        Path absolute = source.toAbsolutePath();
        Path component = absolute.getRoot();
        for (Path part : absolute) {
            component = component.resolve(part);
            if (Files.isSymbolicLink(component)) {
                throw new IllegalArgumentException("Links simbólicos não são permitidos: " + source);
            }
        }
        Path file = absolute.normalize();
        if (!file.startsWith(workStorage) || file.equals(workStorage)) {
            throw new IllegalArgumentException("O arquivo deve estar dentro de storage/cold/work: " + source);
        }
        if (!file.getFileName().toString().endsWith(".dd")) {
            throw new IllegalArgumentException("A evidência deve ser um arquivo .dd: " + source);
        }
        // Resolve antes de abrir o destino: arquivo ausente não deixa ZIP vazio.
        Path real = file.toRealPath();
        if (!real.startsWith(workStorage.toRealPath())) {
            throw new IllegalArgumentException("O arquivo deve estar dentro de storage/cold/work: " + source);
        }
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(real)) {
            throw new IOException("A evidência deve ser um arquivo regular e legível: " + source);
        }
        destination = destination.toAbsolutePath().normalize();
        if (!destination.getParent().equals(real.getParent())
                || !destination.getFileName().toString().endsWith(".zip")) {
            throw new IllegalArgumentException("O ZIP deve ser um arquivo irmão da cópia de trabalho");
        }
        try (var input = Files.newInputStream(real);
             var zip = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE))) {
            zip.putNextEntry(new ZipEntry(real.getFileName().toString()));
            input.transferTo(zip);
            zip.closeEntry();
        }
        return destination;
    }
}
