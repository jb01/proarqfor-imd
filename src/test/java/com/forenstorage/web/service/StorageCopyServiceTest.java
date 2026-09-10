package com.forenstorage.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageCopyServiceTest {
    @TempDir Path directory;
    private Path fast;
    private Path work;
    private Path source;
    private StorageCopyService service;
    private byte[] content;

    @BeforeEach
    void setUp() throws IOException {
        fast = Files.createDirectory(directory.resolve("fast"));
        work = directory.resolve("work");
        content = new byte[100_000];
        Arrays.fill(content, (byte) 37);
        source = Files.write(fast.resolve("synthetic.dd"), content);
        service = spy(new StorageCopyService(fast.toString(), work.toString(), directory.resolve("archive").toString()));
    }

    @Test
    void copiesBeforeDeletingOnlyConfirmedSourceAndPreservesWork() throws IOException {
        var copy = service.copy(1L, source);
        assertArrayEquals(content, Files.readAllBytes(copy.destination()));
        assertArrayEquals(content, Files.readAllBytes(source));
        assertTrue(copy.destination().startsWith(work.resolve("1")));
        service.removeOriginal(copy);
        assertFalse(Files.exists(source));
        assertArrayEquals(content, Files.readAllBytes(copy.destination()));
    }

    enum Failure { READ, WRITE, INPUT_CLOSE, OUTPUT_CLOSE, SHORT_READ, SIZE_CHANGED }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void failedCopyNeverAuthorizesDeletion(Failure failure) throws Exception {
        if (failure == Failure.SHORT_READ) {
            doReturn(new ByteArrayInputStream(new byte[3])).when(service).openSource(source);
        } else if (failure == Failure.READ || failure == Failure.INPUT_CLOSE) {
            doAnswer(invocation -> new FilterInputStream((InputStream) invocation.callRealMethod()) {
                @Override public long transferTo(OutputStream output) throws IOException {
                    if (failure == Failure.READ) {
                        output.write(new byte[3]);
                        throw new IOException("synthetic read failure");
                    }
                    return super.transferTo(output);
                }
                @Override public void close() throws IOException {
                    super.close();
                    if (failure == Failure.INPUT_CLOSE) throw new IOException("synthetic close failure");
                }
            }).when(service).openSource(source);
        } else {
            doAnswer(invocation -> new FilterOutputStream((OutputStream) invocation.callRealMethod()) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (failure == Failure.WRITE) throw new IOException("synthetic write failure");
                    out.write(bytes, offset, length);
                }
                @Override public void close() throws IOException {
                    super.close();
                    if (failure == Failure.OUTPUT_CLOSE) throw new IOException("synthetic close failure");
                    if (failure == Failure.SIZE_CHANGED) Files.writeString(source, "changed");
                }
            }).when(service).openDestination(any());
        }
        assertThrows(IOException.class, () -> service.copy(1L, source));
        assertTrue(Files.exists(source));
        verify(service, never()).deleteOriginal(any());
    }

    @Test
    void refusesForeignDestinationAndUsesFreshDirectoryForNextCopy() throws Exception {
        Path[] collision = new Path[1];
        doAnswer(invocation -> {
            collision[0] = invocation.getArgument(0);
            Files.writeString(collision[0], "foreign");
            return invocation.callRealMethod();
        }).doCallRealMethod().when(service).openDestination(any());
        assertThrows(IOException.class, () -> service.copy(1L, source));
        var copy = service.copy(1L, source);
        assertNotEquals(collision[0], copy.destination());
        assertEquals("foreign", Files.readString(collision[0]));
        assertArrayEquals(content, Files.readAllBytes(source));
    }

    @Test
    void changedSourceOrDestinationBlocksDeletion() throws Exception {
        var first = service.copy(1L, source);
        Files.writeString(first.destination(), "truncated");
        assertThrows(IOException.class, () -> service.removeOriginal(first));
        var second = service.copy(1L, source);
        Files.writeString(source, "changed");
        assertThrows(IOException.class, () -> service.removeOriginal(second));
        assertEquals("changed", Files.readString(source));
        verify(service, never()).deleteOriginal(any());
    }

    @Test
    void sourceAndWorkSymlinksAreRejected() throws Exception {
        Path link = Files.createSymbolicLink(fast.resolve("link.dd"), source);
        assertThrows(IllegalArgumentException.class, () -> service.copy(1L, link));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Files.createSymbolicLink(work, outside);
        assertThrows(IllegalArgumentException.class, () -> service.copy(1L, source));
        assertArrayEquals(content, Files.readAllBytes(source));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
    }

    @Test
    void rejectsOverlappingRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> new StorageCopyService(fast.toString(), fast.resolve("work").toString(), work.toString()));
    }

    @Test
    void preservesConfirmedCopyIfDeletionFails() throws Exception {
        var copy = service.copy(1L, source);
        doThrow(new IOException("synthetic delete failure")).when(service).deleteOriginal(source);
        assertThrows(IOException.class, () -> service.removeOriginal(copy));
        assertArrayEquals(content, Files.readAllBytes(source));
        assertArrayEquals(content, Files.readAllBytes(copy.destination()));
    }
}
