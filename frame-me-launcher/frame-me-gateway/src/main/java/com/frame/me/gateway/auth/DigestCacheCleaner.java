package com.frame.me.gateway.auth;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
public final class DigestCacheCleaner implements AutoCloseable {

    private static final Pattern OWNER = Pattern.compile("me-digest-(\\d+)-(\\d+)-.+\\.body");

    private final Path directory;
    private final Duration maxAge;
    private Disposable sweepTask;

    public DigestCacheCleaner(Path directory, Duration maxAge) {
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("digest-cache-max-age 必须大于零");
        }
        this.directory = directory;
        this.maxAge = maxAge;
    }

    private static boolean ownedByOtherLiveProcess(Path path) {
        var owner = OWNER.matcher(path.getFileName().toString());
        if (!owner.matches()) {
            return false;
        }
        long pid = Long.parseLong(owner.group(1));
        long start = Long.parseLong(owner.group(2));
        if (pid == ProcessHandle.current().pid()) {
            return false;
        }
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                .filter(process -> start == 0 || process.info().startInstant()
                        .map(instant -> instant.toEpochMilli() == start).orElse(true))
                .isPresent();
    }

    public void start() {
        sweepTask = Flux.interval(Duration.ofHours(1)).startWith(0L)
                .concatMap(tick -> Mono.fromRunnable(this::sweep).subscribeOn(Schedulers.boundedElastic()))
                .subscribe();
    }

    void sweep() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().startsWith("me-digest-"))
                    .filter(path -> path.getFileName().toString().endsWith(".body"))
                    .forEach(path -> deleteStale(path, cutoff));
        } catch (IOException | RuntimeException error) {
            log.warn("请求体残留缓存扫描失败", error);
        }
    }

    private void deleteStale(Path path, Instant cutoff) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
                    || DigestBodyCache.isActive(path) || ownedByOtherLiveProcess(path)) {
                return;
            }
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.tryLock()) {
                if (lock == null) {
                    return;
                }
                Files.deleteIfExists(path);
            }
        } catch (OverlappingFileLockException ignored) {
            // 同 JVM 活跃请求持锁，不能删除。
        } catch (IOException | RuntimeException error) {
            log.warn("请求体残留缓存删除失败", error);
        }
    }

    @Override
    public void close() {
        if (sweepTask != null) {
            sweepTask.dispose();
        }
    }
}
