package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;

class NotificationErrorHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationErrorHandler handler = new NotificationErrorHandler(registry);

    @Test
    void handle_rethrows_whenRetryable() {
        // Given
        var ex = new SqsRetryableException("transient");

        // When / Then
        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex))
            .isInstanceOf(SqsRetryableException.class);
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "retry").count()).isEqualTo(1);
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "discarded").count()).isZero();
    }

    @Test
    void handle_returns_whenNonRetryable() {
        // Given
        var ex = new SqsNonRetryableException("poison");

        // When / Then
        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex));
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "discarded").count()).isEqualTo(1);
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "retry").count()).isZero();
    }

    @Test
    void handle_walksNestedCause_toFindClassifiedCause() {
        // Given
        var root = new SqsRetryableException("root");
        var wrapped = new RuntimeException("outer", new RuntimeException("middle", root));

        // When / Then
        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), wrapped))
            .isInstanceOf(SqsRetryableException.class);
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "retry").count()).isEqualTo(1);
    }

    @Test
    void handle_treatsUnclassifiedAsNonRetryable() {
        // Given
        var unknown = new IllegalStateException("unexpected");

        // When / Then
        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), unknown));
        assertThat(registry.counter("notification.ins.sqs.errors", "action", "discarded").count()).isEqualTo(1);
    }
}
