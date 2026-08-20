package uk.gov.defra.trade.imports.ins.backend.consumer;

import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationErrorHandler implements ErrorHandler<Object> {

    private final Counter retryCounter;
    private final Counter discardedCounter;

    public NotificationErrorHandler(MeterRegistry meterRegistry) {
        this.retryCounter = Counter.builder("notification.ins.sqs.errors")
            .tag("action", "retry")
            .description("Errors left for SQS retry (transient)")
            .register(meterRegistry);
        this.discardedCounter = Counter.builder("notification.ins.sqs.errors")
            .tag("action", "discarded")
            .description("Errors discarded as non-retryable (message deleted)")
            .register(meterRegistry);
    }

    @Override
    public void handle(Message<Object> message, Throwable t) {
        Throwable classified = findClassifiedCause(t);
        if (classified instanceof SqsRetryableException retryableException) {
            retryCounter.increment();
            log.warn("Retryable error, message left for retry: {}", classified.getMessage());
            throw retryableException;
        }

        discardedCounter.increment();
        log.error("Non-retryable error, message will be deleted: {}", classified.getMessage(), t);
    }

    private Throwable findClassifiedCause(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SqsRetryableException || current instanceof SqsNonRetryableException) {
                return current;
            }
            current = current.getCause();
        }
        return t;
    }
}
