package uk.gov.defra.trade.imports.ins.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationUpsertService {

  private static final String AGGREGATE_VERSION = "aggregateVersion";
  private final MongoTemplate mongoTemplate;

    public void upsert(JsonNode body) {
        String aggregateId = body.path("aggregateId").asText(null);
        long incomingVersion = body.path(AGGREGATE_VERSION).asLong(-1);

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new SqsNonRetryableException("Missing aggregateId in event body");
        }
        if (incomingVersion < 0) {
            throw new SqsNonRetryableException("Missing or negative aggregateVersion in event body for aggregateId=" + aggregateId);
        }

        JsonNode data = body.path("data");
        JsonNode exchangedDocument = data.path("exchangedDocument");
        JsonNode specifiedConsignment = data.path("specifiedConsignment");

        String referenceNumber = textOrNull(exchangedDocument.path("identifier"));
        String status = textOrNull(exchangedDocument.path("notificationStatusCode"));
        String lastUpdatedStr = textOrNull(exchangedDocument.path("issueDateTime"));

        String originCountry = textOrNull(
            specifiedConsignment.path("originCountry").path("code").path("value"));

        String arrivalDateStr = textOrNull(
            specifiedConsignment
                .path("mainCarriageLogisticsTransportMovement").path(0)
                .path("arrivalEvent").path(0)
                .path("scheduledOccurrenceDateTime"));

        // commodity is intentionally omitted: applicableClassification[0].classCode.value carries
        // the commodity type (e.g. "Domestic") not the animal name (e.g. "Dog"). The animal name
        // lives in Commodity.name in animals-backend but is not included in the outbox event payload.
        // See EUDPA-348 for the animals-backend fix to emit Commodity.name in the outbox event.

        Instant lastUpdated;
        if (lastUpdatedStr != null) {
            try {
                lastUpdated = Instant.parse(lastUpdatedStr);
            } catch (DateTimeParseException e) {
                throw new SqsNonRetryableException(
                    "Invalid issueDateTime for aggregateId=" + aggregateId, e);
            }
        } else {
            lastUpdated = Instant.now();
        }

        Instant arrivalDate = null;
        if (arrivalDateStr != null) {
            try {
                arrivalDate = Instant.parse(arrivalDateStr);
            } catch (DateTimeParseException e) {
                throw new SqsNonRetryableException(
                    "Invalid scheduledOccurrenceDateTime for aggregateId=" + aggregateId, e);
            }
        }

        // Single atomic operation: only applies when the stored version is lower than the incoming
        // version. Concurrent events for the same notification and out-of-order redelivery both
        // converge to the highest applied version without a read-then-write race.
        Update update = new Update()
            .setOnInsert("_id", aggregateId)
            .set(AGGREGATE_VERSION, incomingVersion)
            .set("lastUpdated", lastUpdated);

        if (referenceNumber != null) update.set("referenceNumber", referenceNumber);
        if (status != null) update.set("status", status);
        if (originCountry != null) update.set("originCountry", originCountry);
        if (arrivalDate != null) update.set("arrivalDate", arrivalDate);

        Query query = Query.query(
            Criteria.where("_id").is(aggregateId)
                .and(AGGREGATE_VERSION).lt(incomingVersion));

        mongoTemplate.upsert(query, update, AggregatedNotification.class);

        log.info("Upserted aggregatedNotification: aggregateId={}, aggregateVersion={}", aggregateId, incomingVersion);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
