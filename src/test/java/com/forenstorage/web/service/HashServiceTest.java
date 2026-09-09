package com.forenstorage.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HashServiceTest {
    @TempDir
    Path directory;

    private final HashService service = new HashService();

    @Test
    void calculatesKnownHashInLowercase() throws IOException {
        Path file = directory.resolve("abc.dd");
        Files.writeString(file, "abc", StandardCharsets.US_ASCII);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                service.calculateSha256(file));
    }

    @Test
    void calculatesKnownHashAcrossMultipleBuffers() throws IOException {
        Path file = directory.resolve("million-a.dd");
        byte[] block = new byte[1000];
        Arrays.fill(block, (byte) 'a');
        try (var output = Files.newOutputStream(file)) {
            for (int i = 0; i < 1000; i++) {
                output.write(block);
            }
        }

        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
                service.calculateSha256(file));
    }

    @Test
    void calculatesEmptyFileHash() throws IOException {
        Path file = Files.createFile(directory.resolve("empty.dd"));

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                service.calculateSha256(file));
    }

    @Test
    void reportsMissingFileClearly() {
        Path missing = directory.resolve("missing.dd");

        IOException error = assertThrows(IOException.class, () -> service.calculateSha256(missing));
        assertEquals("Arquivo não encontrado: " + missing, error.getMessage());
        assertInstanceOf(NoSuchFileException.class, error.getCause());
        assertFalse(Files.exists(missing));
    }
}
