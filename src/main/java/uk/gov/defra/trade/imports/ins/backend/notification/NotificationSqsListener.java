package uk.gov.defra.trade.imports.ins.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.annotation.Timed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.gov.defra.trade.imports.ins.backend.logging.TraceContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSqsListener {

    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final ObjectMapper objectMapper;
    private final NotificationUpsertService upsertService;
    private final NotificationMessageMetrics messageMetrics;

    @SqsListener("${aws.sqs.notification.queue-url}")
    @Timed("listener.notification.time")
    public void receive(
            String body,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, required = false) String aggregateId,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                required = false) String receiveCount,
            @Header(name = CORRELATION_ID_ATTRIBUTE, required = false) String correlationId,
            @Header(MessageHeaders.ID) UUID messageId) {

        TraceContext.runWithTraceId(
            traceIdOf(correlationId, messageId), () -> handle(body, aggregateId, receiveCount));
    }

    private static String traceIdOf(String correlationId, UUID messageId) {
        return correlationId != null && !correlationId.isBlank() ? correlationId : messageId.toString();
    }

    private void handle(String body, String aggregateId, String receiveCount) {
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
        switch (upsertService.upsert(parsedBody)) {
            case STORED -> messageMetrics.recordProcessed();
            case SKIPPED_STALE -> messageMetrics.recordSkipped();
        }
    }
}
