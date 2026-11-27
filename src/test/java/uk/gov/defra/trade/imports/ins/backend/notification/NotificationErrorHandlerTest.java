package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    private final NotificationErrorHandler handler = new NotificationErrorHandler(new NotificationMessageMetrics(registry));

    @Test
    void handle_logsAggregateIdMessageIdAndThrowable_whenNonRetryable() {
        Message<Object> message = new GenericMessage<>("body",
            Map.of(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, "agg-1"));
        var ex = new SqsNonRetryableException("poison");

        List<ILoggingEvent> events = logEventsWhileHandling(message, ex);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                .contains("aggregateId=agg-1")
                .contains("messageId=" + message.getHeaders().getId());
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("poison");
        });
    }

    @Test
    void handle_rethrows_whenRetryable() {
        var ex = new SqsRetryableException("transient");

        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex))
            .isInstanceOf(SqsRetryableException.class);
    }

    @Test
    void handle_countsRetriedAndNotDiscarded_whenRetryable() {
        var ex = new SqsRetryableException("transient");

        catchThrowable(() -> handler.handle(new GenericMessage<>("body"), ex));

        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "retried").count()).isEqualTo(1);
        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "discarded").count()).isZero();
    }

    @Test
    void handle_returns_whenNonRetryable() {
        var ex = new SqsNonRetryableException("poison");

        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), ex));
    }

    @Test
    void handle_countsDiscardedAndNotRetried_whenNonRetryable() {
        var ex = new SqsNonRetryableException("poison");

        handler.handle(new GenericMessage<>("body"), ex);

        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "discarded").count()).isEqualTo(1);
        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "retried").count()).isZero();
    }

    @Test
    void handle_walksNestedCause_toFindClassifiedCause() {
        var root = new SqsRetryableException("root");
        var wrapped = new RuntimeException("outer", new RuntimeException("middle", root));

        assertThatThrownBy(() -> handler.handle(new GenericMessage<>("body"), wrapped))
            .isInstanceOf(SqsRetryableException.class);
    }

    @Test
    void handle_countsRetried_whenClassifiedCauseIsNested() {
        var root = new SqsRetryableException("root");
        var wrapped = new RuntimeException("outer", new RuntimeException("middle", root));

        catchThrowable(() -> handler.handle(new GenericMessage<>("body"), wrapped));

        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "retried").count()).isEqualTo(1);
    }

    @Test
    void handle_treatsUnclassifiedAsNonRetryable() {
        var unknown = new IllegalStateException("unexpected");

        assertThatNoException().isThrownBy(() -> handler.handle(new GenericMessage<>("body"), unknown));
    }

    @Test
    void handle_countsDiscarded_whenUnclassified() {
        var unknown = new IllegalStateException("unexpected");

        handler.handle(new GenericMessage<>("body"), unknown);

        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "discarded").count()).isEqualTo(1);
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
}
