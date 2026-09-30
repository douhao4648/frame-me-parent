package com.frame.me.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DigestCacheCleanerTest {
    @TempDir
    Path directory;

    @Test
    void removesOnlyStaleMatchingRegularFiles() throws Exception {
        Path stale = oldFile("me-digest-old.body");
        Path unrelated = oldFile("other.body");
        Path fresh = Files.writeString(directory.resolve("me-digest-fresh.body"), "fresh");
        Path link = directory.resolve("me-digest-link.body");
        Files.createSymbolicLink(link, unrelated);
        new DigestCacheCleaner(directory, Duration.ofHours(24)).sweep();
        assertThat(stale).doesNotExist();
        assertThat(unrelated).exists();
        assertThat(fresh).exists();
        assertThat(Files.isSymbolicLink(link)).isTrue();
    }

    @Test
    void skipsLockedActiveFileAndRemovesItAfterOwnerReleasesLock() throws Exception {
        Path active = oldFile("me-digest-active.body");
        var cleaner = new DigestCacheCleaner(directory, Duration.ofHours(24));
        try (var channel = FileChannel.open(active, StandardOpenOption.WRITE); var lock = channel.lock()) {
            cleaner.sweep();
            assertThat(active).exists();
        }
        cleaner.sweep();
        assertThat(active).doesNotExist();
    }

    @Test
    void protectsActualRequestCacheDuringSweep() throws Exception {
        var cache = new DigestBodyCache(directory, 0, 1024);
        cache.append(org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{1}));
        Path active = ((CachedRequestBody.File) cache.body()).path();
        try {
            Files.setLastModifiedTime(active, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
            new DigestCacheCleaner(directory, Duration.ofHours(24)).sweep();
            assertThat(active).exists();
        } finally {
            cache.cleanup().block();
        }
        assertThat(active).doesNotExist();
    }

    @Test
    void startupRunsSweepAndCloseStopsTask() throws Exception {
        Path stale = oldFile("me-digest-startup.body");
        try (var cleaner = new DigestCacheCleaner(directory, Duration.ofHours(24))) {
            cleaner.start();
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (Files.exists(stale) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(stale).doesNotExist();
        }
    }

    @Test
    void skipsOtherLiveProcessAndCleansItsFilesAfterExit() throws Exception {
        Process owner = new ProcessBuilder("/bin/sleep", "10").start();
        long start = owner.info().startInstant().orElseThrow().toEpochMilli();
        Path active = oldFile("me-digest-" + owner.pid() + "-" + start + "-owner.body");
        var cleaner = new DigestCacheCleaner(directory, Duration.ofHours(24));
        try {
            cleaner.sweep();
            assertThat(active).exists();
        } finally {
            owner.destroyForcibly();
            owner.waitFor();
        }
        cleaner.sweep();
        assertThat(active).doesNotExist();
    }

    private Path oldFile(String name) throws Exception {
        Path path = Files.writeString(directory.resolve(name), "old");
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        return path;
    }
}
