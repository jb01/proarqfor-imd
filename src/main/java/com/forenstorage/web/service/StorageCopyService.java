package com.forenstorage.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

@Service
public class StorageCopyService {
    private final Path fast;
    private final Path work;

    public StorageCopyService(@Value("${forenstorage.storage.fast:storage/fast}") String fast,
                              @Value("${forenstorage.storage.work:storage/cold/work}") String work,
                              @Value("${forenstorage.storage.archive:storage/cold/archive}") String archive) {
        this.fast = Path.of(fast).toAbsolutePath().normalize();
        this.work = Path.of(work).toAbsolutePath().normalize();
        Path archiveRoot = Path.of(archive).toAbsolutePath().normalize();
        if (overlap(this.fast, this.work) || overlap(this.fast, archiveRoot) || overlap(this.work, archiveRoot)) {
            throw new IllegalArgumentException("Fast, work e archive devem ser diretórios separados");
        }
    }

    private static boolean overlap(Path a, Path b) {
        return a.startsWith(b) || b.startsWith(a);
    }

    Path validateSource(Path source) throws IOException {
        Path result = regularUnder(source, fast);
        if (!result.getFileName().toString().endsWith(".dd")) {
            throw new IllegalArgumentException("Arquivamento exige .dd; remova o registro e cadastre novamente um .dd");
        }
        return result;
    }

    ConfirmedCopy copy(Long id, Path source) throws IOException {
        source = validateSource(source);
        BasicFileAttributes original = attributes(source);
        Path evidenceDirectory = work.resolve(id.toString());
        rejectSymlinks(evidenceDirectory);
        Files.createDirectories(evidenceDirectory);
        rejectSymlinks(evidenceDirectory);
        Path attempt = Files.createTempDirectory(evidenceDirectory, "copy-");
        Path destination = attempt.resolve(source.getFileName());
        long count;
        try (var input = openSource(source); var output = openDestination(destination)) {
            count = input.transferTo(output);
        }
        BasicFileAttributes copied = attributes(destination);
        requireUnchanged(original, attributes(source));
        if (count != original.size() || copied.size() != original.size()) {
            throw new IOException("Cópia incompleta ou tamanho divergente");
        }
        return new ConfirmedCopy(source, destination, original, copied);
    }

    void removeOriginal(ConfirmedCopy copy) throws IOException {
        // Only a copy returned after EOF, successful closes and size checks can reach this method.
        validateSource(copy.source);
        regularUnder(copy.destination, work);
        requireUnchanged(copy.original, attributes(copy.source));
        requireUnchanged(copy.copied, attributes(copy.destination));
        deleteOriginal(copy.source);
    }

    static Path regularUnder(Path path, Path root) throws IOException {
        rejectSymlinks(path.toAbsolutePath());
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("Arquivo fora do storage permitido");
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(normalized)) {
            throw new IOException("Arquivo inexistente ou não regular/legível");
        }
        Path real = normalized.toRealPath();
        if (!real.startsWith(root.toRealPath())) {
            throw new IllegalArgumentException("Arquivo fora do storage permitido");
        }
        return real;
    }

    static void rejectSymlinks(Path path) {
        Path component = path.getRoot();
        for (Path part : path) {
            component = component.resolve(part);
            if (Files.isSymbolicLink(component)) {
                throw new IllegalArgumentException("Links simbólicos não são permitidos");
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireUnchanged(BasicFileAttributes before, BasicFileAttributes after) throws IOException {
        if (!after.isRegularFile() || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Arquivo alterado durante a operação; exclusão bloqueada");
        }
    }

    InputStream openSource(Path source) throws IOException { return Files.newInputStream(source); }
    OutputStream openDestination(Path destination) throws IOException {
        return Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    void deleteOriginal(Path source) throws IOException { Files.delete(source); }

    static final class ConfirmedCopy {
        private final Path source;
        private final Path destination;
        private final BasicFileAttributes original;
        private final BasicFileAttributes copied;

        private ConfirmedCopy(Path source, Path destination, BasicFileAttributes original, BasicFileAttributes copied) {
            this.source = source;
            this.destination = destination;
            this.original = original;
            this.copied = copied;
        }
        Path destination() { return destination; }
    }
}
