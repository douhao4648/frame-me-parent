package com.frame.me.gateway.auth;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class BodyDigestVerifierTest {

    @TempDir
    Path directory;

    @Test
    void verifiesEveryChunkAndReplaysCompleteLargeBody() throws Exception {
        byte[] body = "a".repeat(2 * 1024 * 1024 + 17).getBytes(StandardCharsets.UTF_8);
        Flux<DataBuffer> chunks = Flux.range(0, (body.length + 4095) / 4096)
                .map(i -> DefaultDataBufferFactory.sharedInstance.wrap(
                        Arrays.copyOfRange(body, i * 4096, Math.min(body.length, (i + 1) * 4096))));
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(body, "SHA-256")).body(chunks);
        byte[] replayed = new BodyDigestVerifier(directory).verify(request, BodyDigestVerifierTest::readBody).block();
        assertThat(replayed).isEqualTo(body);
        assertEmptyDirectory();
    }

    @Test
    void rejectsPrefixDigestWithoutForwardingAndDeletesFile() throws Exception {
        byte[] prefix = "a".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8);
        byte[] body = "a".repeat(1024 * 1024).concat("tampered-tail").getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(prefix, "SHA-256"))
                .body(new String(body, StandardCharsets.UTF_8));
        AtomicBoolean forwarded = new AtomicBoolean();
        var result = new BodyDigestVerifier(directory).verify(request, path -> {
            forwarded.set(true);
            return Mono.just(true);
        }).block();
        assertThat(result).isNull();
        assertThat(forwarded).isFalse();
        assertEmptyDirectory();
    }

    @Test
    void supportsEmptyBodyAndSha512() throws Exception {
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(new byte[0], "SHA-512")).build();
        byte[] body = new BodyDigestVerifier(directory).verify(request, BodyDigestVerifierTest::readBody).block();
        assertThat(body).isEmpty();
        assertEmptyDirectory();
    }

    @Test
    void cleansUpOnRequestReadFailure() throws Exception {
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(new byte[0], "SHA-256"))
                .body(Flux.concat(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{1})),
                        Flux.error(new IOException("read failed"))));
        assertThatThrownBy(() -> new BodyDigestVerifier(directory, 0).verify(request, body -> Mono.just(true)).block())
                .hasRootCauseMessage("read failed");
        assertEmptyDirectory();
    }

    @Test
    void cleansUpOnDownstreamFailure() throws Exception {
        byte[] body = "chunk".getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(body, "SHA-256")).body("chunk");
        assertThatThrownBy(() -> new BodyDigestVerifier(directory, 0).verify(request,
                path -> Mono.error(new IllegalStateException("downstream failed"))).block())
                .isInstanceOf(IllegalStateException.class).hasMessage("downstream failed");
        assertEmptyDirectory();
    }

    @Test
    void cancellationStopsSourceReleasesPooledBufferAndDeletesFile() throws Exception {
        var nativeBuffer = Unpooled.buffer().writeBytes("chunk".getBytes(StandardCharsets.UTF_8));
        var buffer = new NettyDataBufferFactory(io.netty.buffer.PooledByteBufAllocator.DEFAULT).wrap(nativeBuffer);
        CountDownLatch read = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        Flux<DataBuffer> source = Flux.concat(Flux.just(buffer), Flux.<DataBuffer>never())
                .doOnNext(b -> read.countDown()).doOnCancel(() -> cancelled.set(true));
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(new byte[0], "SHA-256")).body(source);
        var subscription = new BodyDigestVerifier(directory, 0).verify(request, body -> Mono.just(true)).subscribe();
        try {
            assertThat(read.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.dispose();
        }
        awaitCleanup();
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (nativeBuffer.refCnt() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(cancelled).isTrue();
        assertThat(nativeBuffer.refCnt()).isZero();
    }

    @Test
    void cancellationDuringForwardingDeletesVerifiedFile() throws Exception {
        byte[] bytes = "chunk".getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(bytes, "SHA-256")).body("chunk");
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Path> file = new AtomicReference<>();
        var subscription = new BodyDigestVerifier(directory, 0).verify(request, body -> {
            file.set(((CachedRequestBody.File) body).path());
            entered.countDown();
            return Mono.never();
        }).subscribe();
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(file.get()).exists();
        } finally {
            subscription.dispose();
        }
        awaitCleanup();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 262143, 262144, 262145})
    void switchesToFileOnlyAboveThreshold(int size) throws Exception {
        byte[] bytes = "a".repeat(size).getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(bytes, "SHA-256"))
                .body(new String(bytes, StandardCharsets.UTF_8));
        byte[] replayed = new BodyDigestVerifier(directory).verify(request, body -> {
            assertThat(body).isInstanceOf(size <= 262144 ? CachedRequestBody.Memory.class : CachedRequestBody.File.class);
            return readBody(body);
        }).block();
        assertThat(replayed).isEqualTo(bytes);
        assertEmptyDirectory();
    }

    @Test
    void spillsPreviouslyBufferedChunksWithoutLosingBytes() throws Exception {
        byte[] bytes = "abcdefghijkl".getBytes(StandardCharsets.UTF_8);
        var chunks = Flux.range(0, 4).map(i -> DefaultDataBufferFactory.sharedInstance.wrap(
                Arrays.copyOfRange(bytes, i * 3, i * 3 + 3)));
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(bytes, "SHA-256")).body(chunks);
        byte[] replayed = new BodyDigestVerifier(directory, 8).verify(request, body -> {
            assertThat(body).isInstanceOf(CachedRequestBody.File.class);
            return readBody(body);
        }).block();
        assertThat(replayed).isEqualTo(bytes);
        assertEmptyDirectory();
    }

    @Test
    void smallBodyDoesNotNeedAnAvailableTemporaryDirectory() throws Exception {
        byte[] bytes = "small".getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(bytes, "SHA-256")).body("small");
        byte[] replayed = new BodyDigestVerifier(directory.resolve("missing"), 256).verify(request,
                BodyDigestVerifierTest::readBody).block();
        assertThat(replayed).isEqualTo(bytes);
        assertThat(directory.resolve("missing")).doesNotExist();
    }

    @Test
    void createsConfiguredDirectoryOnlyWhenSpillingAndDeletesCacheFile() throws Exception {
        Path custom = directory.resolve("custom/cache");
        byte[] bytes = "body".getBytes(StandardCharsets.UTF_8);
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest(bytes, "SHA-256")).body("body");
        byte[] replayed = new BodyDigestVerifier(custom, 0).verify(request, body -> {
            assertThat(body).isInstanceOf(CachedRequestBody.File.class);
            assertThat(((CachedRequestBody.File) body).path().getParent()).isEqualTo(custom);
            return readBody(body);
        }).block();
        assertThat(replayed).isEqualTo(bytes);
        try (var files = Files.list(custom)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BodyDigestVerifier(directory, -1));
    }

    private static Mono<byte[]> readBody(CachedRequestBody body) {
        return DataBufferUtils.join(body.replay(DefaultDataBufferFactory.sharedInstance))
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                }).defaultIfEmpty(new byte[0]);
    }

    private static String digest(byte[] body, String algorithm) throws Exception {
        return algorithm + "=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm).digest(body));
    }

    private void assertEmptyDirectory() throws IOException {
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    private void awaitCleanup() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            try (var files = Files.list(directory)) {
                if (files.findAny().isEmpty()) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        assertEmptyDirectory();
    }
}
