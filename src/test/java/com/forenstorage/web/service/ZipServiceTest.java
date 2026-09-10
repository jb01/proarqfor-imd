package com.forenstorage.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ZipServiceTest {
    @TempDir
    Path directory;
    private Path work;
    private ZipService service;

    @BeforeEach
    void setUp() throws IOException {
        work = Files.createDirectories(directory.resolve("storage/cold/work"));
        service = new ZipService(work.toString());
    }

    @Test
    void createsReadableZipWithSingleEntryAndExactBinaryContent() throws IOException {
        Path evidenceDirectory = Files.createDirectory(work.resolve("42"));
        byte[] content = new byte[100_000];
        new Random(42).nextBytes(content);
        Path source = Files.write(evidenceDirectory.resolve("evidencia.dd"), content);

        Path result = service.compress(source);

        assertEquals(evidenceDirectory.resolve("evidencia.dd.zip"), result);
        assertTrue(Files.isRegularFile(result));
        try (var zip = new ZipFile(result.toFile())) {
            assertEquals(1, zip.size());
            var entry = zip.getEntry("evidencia.dd");
            assertNotNull(entry);
            assertFalse(entry.isDirectory());
            assertEquals(content.length, entry.getSize());
            try (var input = zip.getInputStream(entry)) {
                assertArrayEquals(content, input.readAllBytes());
            }
        }
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void compressesEmptyFile() throws IOException {
        Path source = Files.createFile(work.resolve("empty.dd"));
        try (var zip = new ZipFile(service.compress(source).toFile())) {
            assertEquals(1, zip.size());
            assertEquals(0, zip.getEntry("empty.dd").getSize());
        }
        assertTrue(Files.exists(source));
    }

    @Test
    void missingSourceDoesNotCreateZip() {
        Path missing = work.resolve("missing.dd");
        assertThrows(NoSuchFileException.class, () -> service.compress(missing));
        assertFalse(Files.exists(work.resolve("missing.dd.zip")));
    }

    @Test
    void refusesToOverwriteExistingDestination() throws IOException {
        Path source = Files.writeString(work.resolve("evidence.dd"), "original");
        Path destination = Files.writeString(work.resolve("evidence.dd.zip"), "existing artifact");
        assertThrows(FileAlreadyExistsException.class, () -> service.compress(source));
        assertEquals("existing artifact", Files.readString(destination));
        assertEquals("original", Files.readString(source));
    }

    @Test
    void rejectsSourceOutsideWorkIncludingTraversal() throws IOException {
        Path outside = Files.writeString(directory.resolve("outside.dd"), "original");
        assertThrows(IllegalArgumentException.class, () -> service.compress(outside));
        assertThrows(IllegalArgumentException.class,
                () -> service.compress(work.resolve("../../../outside.dd")));
        assertFalse(Files.exists(directory.resolve("outside.dd.zip")));
    }

    @Test
    void rejectsWrongExtensionAndDirectory() throws IOException {
        Path wrong = Files.writeString(work.resolve("evidence.zip.enc"), "ciphertext");
        Path folder = Files.createDirectory(work.resolve("folder.dd"));
        assertThrows(IllegalArgumentException.class, () -> service.compress(wrong));
        assertThrows(IOException.class, () -> service.compress(folder));
    }

    @Test
    void rejectsSourceAndDestinationSymlinks() throws IOException {
        Path source = Files.writeString(work.resolve("evidence.dd"), "original");
        Path link = Files.createSymbolicLink(work.resolve("link.dd"), source);
        assertThrows(IllegalArgumentException.class, () -> service.compress(link));
        Path other = Files.writeString(directory.resolve("other.zip"), "preserve");
        Files.createSymbolicLink(work.resolve("evidence.dd.zip"), other);
        assertThrows(FileAlreadyExistsException.class, () -> service.compress(source));
        assertEquals("preserve", Files.readString(other));
        assertEquals("original", Files.readString(source));
    }
}
