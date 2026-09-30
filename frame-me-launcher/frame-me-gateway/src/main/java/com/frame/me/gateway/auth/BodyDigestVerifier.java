package com.frame.me.gateway.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Slf4j
final class BodyDigestVerifier {

    private final Path directory;
    private final int memoryThreshold;
    private final long maxSize;
    private final Duration readTimeout;

    BodyDigestVerifier() {
        this(Path.of(System.getProperty("java.io.tmpdir")), 256 * 1024);
    }

    BodyDigestVerifier(Path directory) {
        this(directory, 256 * 1024);
    }

    BodyDigestVerifier(Path directory, long memoryThreshold) {
        this(directory, memoryThreshold, 20L * 1024 * 1024, Duration.ofSeconds(60));
    }

    BodyDigestVerifier(Path directory, long memoryThreshold, long maxSize, Duration readTimeout) {
        if (memoryThreshold < 0 || memoryThreshold > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("digest-memory-threshold 必须在 0 到 2147483647 字节之间");
        }
        if (maxSize <= 0 || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("digest-max-size 和 digest-read-timeout 必须大于零");
        }
        this.directory = directory;
        this.memoryThreshold = (int) memoryThreshold;
        this.maxSize = maxSize;
        this.readTimeout = readTimeout;
    }

    private static void update(MessageDigest digest, DataBuffer buffer) {
        try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
            while (iterator.hasNext()) {
                digest.update(iterator.next());
            }
        }
    }

    private static ExpectedDigest parse(String header) {
        if (header == null) {
            return null;
        }
        for (String entry : header.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String algorithm = switch (pair[0].trim().toLowerCase(Locale.ROOT)) {
                case "sha-256" -> "SHA-256";
                case "sha-512" -> "SHA-512";
                default -> null;
            };
            if (algorithm == null) {
                continue;
            }
            try {
                byte[] value = Base64.getDecoder().decode(pair[1].trim());
                return new ExpectedDigest(MessageDigest.getInstance(algorithm), value);
            } catch (IllegalArgumentException e) {
                return null;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(algorithm + " 不可用", e);
            }
        }
        return null;
    }

    <T> Mono<T> verify(ServerHttpRequest request, Function<CachedRequestBody, Mono<T>> verified) {
        return Mono.defer(() -> {
            ExpectedDigest expected = parse(request.getHeaders().getFirst("Digest"));
            if (expected == null) {
                return Mono.empty();
            }
            if (request.getHeaders().getContentLength() > maxSize) {
                return Mono.error(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "请求体超过 digest-max-size"));
            }
            return Mono.usingWhen(
                    Mono.fromSupplier(() -> new DigestBodyCache(directory, memoryThreshold, maxSize)),
                    cache -> request.getBody()
                            .publishOn(Schedulers.boundedElastic(), 1)
                            .doOnNext(buffer -> {
                                try {
                                    update(expected.digest(), buffer);
                                    cache.append(buffer);
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            })
                            .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                            .then().timeout(readTimeout)
                            .onErrorMap(TimeoutException.class, error -> new ResponseStatusException(
                                    HttpStatus.REQUEST_TIMEOUT, "请求体读取超过 digest-read-timeout", error))
                            .then(Mono.defer(() -> {
                                if (!MessageDigest.isEqual(expected.digest().digest(), expected.value())) {
                                    log.warn("应用鉴权失败：请求体摘要与 Digest 头不匹配");
                                    return Mono.empty();
                                }
                                return verified.apply(cache.body());
                            })),
                    DigestBodyCache::cleanup,
                    (cache, error) -> cache.cleanup(),
                    DigestBodyCache::cleanup);
        });
    }

    private record ExpectedDigest(MessageDigest digest, byte[] value) {
    }

}
