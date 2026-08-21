package uk.gov.defra.trade.imports.ins.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationSqsListener {

    private final ObjectMapper objectMapper;
    private final NotificationUpsertService upsertService;
    private final Counter processedCounter;

    public NotificationSqsListener(
            ObjectMapper objectMapper,
            NotificationUpsertService upsertService,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.upsertService = upsertService;
        this.processedCounter = Counter.builder("notification.ins.sqs.messages")
            .tag("outcome", "processed")
            .description("Messages successfully processed and upserted")
            .register(meterRegistry);
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, required = false) String aggregateId,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                required = false) String receiveCount) {

        log.info("Received notification event: aggregateId={}, receiveCount={}, bodyLength={}",
            aggregateId, receiveCount, body != null ? body.length() : 0);

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new SqsNonRetryableException("Missing or blank MESSAGE_GROUP_ID: " + aggregateId);
        }

        if (body == null || body.isBlank()) {
            throw new SqsNonRetryableException("Empty message body for aggregateId=" + aggregateId);
        }

        JsonNode parsedBody;
        try {
            parsedBody = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Message body is not valid JSON for aggregateId=" + aggregateId, e);
        }

        String eventType = parsedBody.path("eventType").asText();
        OutboxEventType resolved = OutboxEventType.fromWireValue(eventType).orElseThrow(() -> {
            // An unrecognised type means the store is about to diverge — dead-letter with context
            // rather than silently acknowledge.
            log.error("Unrecognised eventType={} aggregateId={} — dead-lettering", eventType, aggregateId);
            return new SqsNonRetryableException(
                "Unrecognised eventType=" + eventType + " for aggregateId=" + aggregateId);
        });

        log.debug("Processing eventType={} aggregateId={}", resolved, aggregateId);
        upsertService.upsert(resolved, parsedBody);
        processedCounter.increment();
    }
}
