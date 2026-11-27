package uk.gov.defra.trade.imports.ins.backend.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageMetrics {

    private final Counter processed;
    private final Counter retried;
    private final Counter discarded;

    public NotificationMessageMetrics(MeterRegistry meterRegistry) {
        processed = counter(meterRegistry, "processed", "Messages processed and stored");
        retried = counter(meterRegistry, "retried", "Messages left on the queue for another attempt");
        discarded = counter(meterRegistry, "discarded", "Messages deleted unprocessed");
    }

    public void recordProcessed() {
        processed.increment();
    }

    public void recordRetried() {
        retried.increment();
    }

    public void recordDiscarded() {
        discarded.increment();
    }

    private static Counter counter(MeterRegistry meterRegistry, String outcome, String description) {
        return Counter.builder("notification.ins.sqs.messages")
            .tag("outcome", outcome)
            .description(description)
            .register(meterRegistry);
    }
}
