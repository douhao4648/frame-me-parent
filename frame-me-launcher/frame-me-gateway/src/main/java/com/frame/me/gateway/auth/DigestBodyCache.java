package com.frame.me.gateway.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
final class DigestBodyCache {

    private static final Set<Path> ACTIVE_FILES = ConcurrentHashMap.newKeySet();
    private static final String OWNER = ProcessHandle.current().pid() + "-"
            + ProcessHandle.current().info().startInstant().orElse(Instant.EPOCH).toEpochMilli() + "-";

    private final Path directory;
    private final int threshold;
    private final long maxSize;
    private long size;
    private ByteArrayOutputStream memory;
    private Path path;
    private Path activePath;
    private FileChannel channel;
    private boolean closed;

    DigestBodyCache(Path directory, int threshold, long maxSize) {
        this.directory = directory;
        this.threshold = threshold;
        this.maxSize = maxSize;
        this.memory = new ByteArrayOutputStream(Math.min(threshold, 8192));
    }

    static boolean isActive(Path path) throws IOException {
        return ACTIVE_FILES.contains(path.toRealPath());
    }

    synchronized void append(DataBuffer buffer) {
        if (closed) {
            throw new IllegalStateException("请求体缓存已关闭");
        }
        if (buffer.readableByteCount() > maxSize - size) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "请求体超过 digest-max-size");
        }
        size += buffer.readableByteCount();
        try {
            if (channel == null && (long) memory.size() + buffer.readableByteCount() > threshold) {
                Files.createDirectories(directory);
                path = Files.createTempFile(directory, "me-digest-" + OWNER, ".body");
                activePath = path.toRealPath();
                ACTIVE_FILES.add(activePath);
                channel = FileChannel.open(path, StandardOpenOption.WRITE);
                channel.lock();
                write(ByteBuffer.wrap(memory.toByteArray()));
                memory = null;
            }
            try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
                while (iterator.hasNext()) {
                    ByteBuffer bytes = iterator.next();
                    if (channel != null) {
                        write(bytes);
                    } else {
                        byte[] chunk = new byte[bytes.remaining()];
                        bytes.get(chunk);
                        memory.writeBytes(chunk);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    synchronized CachedRequestBody body() {
        if (channel != null) {
            return new CachedRequestBody.File(path);
        }
        byte[] bytes = memory.toByteArray();
        memory = null;
        return new CachedRequestBody.Memory(bytes);
    }

    Mono<Void> cleanup() {
        return Mono.fromRunnable(this::close).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, error -> {
                    log.warn("请求体缓存清理失败，将由残留文件清扫重试", error);
                    return Mono.empty();
                }).then();
    }

    private void write(ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) {
            channel.write(bytes);
        }
    }

    private synchronized void close() {
        closed = true;
        memory = null;
        try {
            try {
                if (channel != null) {
                    channel.close();
                }
            } finally {
                if (path != null) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (activePath != null) {
                ACTIVE_FILES.remove(activePath);
            }
        }
    }
}
