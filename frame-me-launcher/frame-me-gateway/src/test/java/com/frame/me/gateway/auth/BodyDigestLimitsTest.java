package com.frame.me.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class BodyDigestLimitsTest {
    @TempDir
    Path directory;

    @Test
    void rejectsDeclaredOversizeWithoutReadingBody() throws Exception {
        AtomicBoolean read = new AtomicBoolean();
        var request = MockServerHttpRequest.post("/upload").header("Content-Length", "9")
                .header("Digest", digest("body"))
                .body(Flux.defer(() -> {
                    read.set(true);
                    return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[9]));
                }));
        assertThatThrownBy(() -> verifier(8, Duration.ofSeconds(1)).verify(request, body -> Mono.just(true)).block())
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
        assertThat(read).isFalse();
        assertNoFiles();
    }

    @Test
    void acceptsExactLimitButRejectsChunkedOverflowAndCleansFile() throws Exception {
        var exact = MockServerHttpRequest.post("/upload").header("Digest", digest("12345678")).body("12345678");
        assertThat(verifier(8, Duration.ofSeconds(1)).verify(exact, body -> Mono.just(true)).block()).isTrue();
        var overflow = MockServerHttpRequest.post("/upload").header("Digest", digest("123456789"))
                .body(Flux.just(buffer("1234"), buffer("5678"), buffer("9")));
        AtomicBoolean forwarded = new AtomicBoolean();
        assertThatThrownBy(() -> verifier(8, Duration.ofSeconds(1)).verify(overflow, body -> {
            forwarded.set(true);
            return Mono.just(true);
        }).block()).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
        assertThat(forwarded).isFalse();
        assertNoFiles();
    }

    @Test
    void totalReadDeadlineStopsContinuousTrickleAndCleansFile() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        var source = Flux.interval(Duration.ofMillis(10)).map(tick -> buffer("x"))
                .doOnCancel(() -> cancelled.set(true));
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest("x")).body(source);
        assertThatThrownBy(() -> verifier(1024, Duration.ofMillis(200)).verify(request, body -> Mono.just(true)).block())
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(408));
        assertThat(cancelled).isTrue();
        assertNoFiles();
    }

    @Test
    void readDeadlineDoesNotLimitDownstreamWork() throws Exception {
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest("body")).body("body");
        assertThat(verifier(8, Duration.ofMillis(100)).verify(request,
                body -> Mono.delay(Duration.ofMillis(200)).thenReturn(true)).block()).isTrue();
        assertNoFiles();
    }

    @Test
    void cleanupFailureDoesNotReplaceSuccessfulResult() throws Exception {
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest("body")).body("body");
        Boolean result = verifier(8, Duration.ofSeconds(1)).verify(request, body -> Mono.fromCallable(() -> {
            preventDeletion(body);
            return true;
        })).block();
        assertThat(result).isTrue();
    }

    @Test
    void cleanupFailurePreservesOriginalDownstreamError() throws Exception {
        var request = MockServerHttpRequest.post("/upload").header("Digest", digest("body")).body("body");
        assertThatThrownBy(() -> verifier(8, Duration.ofSeconds(1)).verify(request, body -> Mono.fromCallable(() -> {
            preventDeletion(body);
            throw new IllegalStateException("original failure");
        })).block()).isInstanceOf(IllegalStateException.class).hasMessage("original failure");
    }

    private void preventDeletion(CachedRequestBody body) throws Exception {
        Path file = ((CachedRequestBody.File) body).path();
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("child"), "simulate delete failure");
    }

    private BodyDigestVerifier verifier(long maxSize, Duration timeout) {
        return new BodyDigestVerifier(directory, 0, maxSize, timeout);
    }

    private static String digest(String body) throws Exception {
        return "SHA-256=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static DataBuffer buffer(String value) {
        return DefaultDataBufferFactory.sharedInstance.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private void assertNoFiles() throws Exception {
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }
}
