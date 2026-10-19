package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

class NotificationErrorHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationErrorHandler handler = new NotificationErrorHandler(registry);

    @Test
    void handle_logsAggregateIdMessageIdAndThrowable_whenNonRetryable() {
        // Given
        Message<Object> message = new GenericMessage<>("body",
            Map.of(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, "agg-1"));
        var ex = new SqsNonRetryableException("poison");

        // When
        List<ILoggingEvent> events = logEventsWhileHandling(message, ex);

        // Then
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                .contains("aggregateId=agg-1")
                .contains("messageId=" + message.getHeaders().getId());
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("poison");
        });
    }

    private List<ILoggingEvent> logEventsWhileHandling(Message<Object> message, Throwable ex) {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationErrorHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handle(message, ex);
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

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
