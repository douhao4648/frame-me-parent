package com.frame.me.gateway.auth;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;

public sealed interface CachedRequestBody {

    Flux<DataBuffer> replay(DataBufferFactory factory);

    record Memory(byte[] bytes) implements CachedRequestBody {
        @Override
        public Flux<DataBuffer> replay(DataBufferFactory factory) {
            return Flux.defer(() -> Flux.just(factory.wrap(bytes)));
        }
    }

    record File(Path path) implements CachedRequestBody {
        @Override
        public Flux<DataBuffer> replay(DataBufferFactory factory) {
            return DataBufferUtils.read(path, factory, 64 * 1024).subscribeOn(Schedulers.boundedElastic());
        }
    }
}
