package uk.gov.defra.trade.imports.ins.backend.consumer;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;

class NotificationErrorHandlerTest {

    private final NotificationErrorHandler handler =
        new NotificationErrorHandler(new SimpleMeterRegistry());

    @Test
    void handle_rethrows_whenRetryable() {
        var ex = new SqsRetryableException("transient");
        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex))
            .isInstanceOf(SqsRetryableException.class);
    }

    @Test
    void handle_returns_whenNonRetryable() {
        var ex = new SqsNonRetryableException("poison");
        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex));
    }

    @Test
    void handle_walksNestedCause_toFindClassifiedCause() {
        var root = new SqsRetryableException("root");
        var wrapped = new RuntimeException("outer", new RuntimeException("middle", root));
        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), wrapped))
            .isInstanceOf(SqsRetryableException.class);
    }

    @Test
    void handle_treatsUnclassifiedAsNonRetryable() {
        var unknown = new IllegalStateException("unexpected");
        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), unknown));
    }
}
