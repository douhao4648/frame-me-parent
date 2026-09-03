package com.frame.me.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.frame.me.gateway.config.GatewayAccessLogProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayAccessLogFilter} 测试.
 *
 * @author frame-me
 */
class GatewayAccessLogFilterTest {

    private final GatewayAccessLogProperties props = new GatewayAccessLogProperties();
    private final GatewayAccessLogFilter filter = new GatewayAccessLogFilter(props);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GatewayAccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void logsMethodUriStatusCost() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/health?verbose=true"));
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .startsWith("log-access: ")
                .contains("method=GET", "uri=/api/health?verbose=true", "status=200", "cost=");
    }

    @Test
    void longUri_truncatedWithEllipsis() {
        props.setMaxLength(10);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/flex/demo/list?keyword=abcdefghijklmnopqrstuvwxyz"));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("uri=/api/flex/...");
        assertThat(message).doesNotContain("keyword");
    }

    @Test
    void maxLengthZero_noTruncation() {
        props.setMaxLength(0);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/flex/demo/list?keyword=abcdefghijklmnopqrstuvwxyz"));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(appender.list.get(0).getFormattedMessage()).contains("keyword=abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void orderIsOutermost() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
