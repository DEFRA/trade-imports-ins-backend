package uk.gov.defra.trade.imports.ins.backend.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.ins.backend.store.AggregatedNotification;

@Slf4j
@Service
public class NotificationUpsertService {

    private final MongoTemplate mongoTemplate;

    public NotificationUpsertService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void upsert(JsonNode body) {
        String aggregateId = body.path("aggregateId").asText(null);
        long incomingVersion = body.path("aggregateVersion").asLong(-1);

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

        String arrivalDate = textOrNull(
            specifiedConsignment
                .path("mainCarriageLogisticsTransportMovement").path(0)
                .path("arrivalEvent").path(0)
                .path("scheduledOccurrenceDateTime"));

        String commodity = textOrNull(
            specifiedConsignment
                .path("includedConsignmentItem").path(0)
                .path("includedTradeLineItem").path(0)
                .path("applicableClassification").path(0)
                .path("classCode").path("value"));

        Instant lastUpdated = lastUpdatedStr != null ? Instant.parse(lastUpdatedStr) : Instant.now();

        // Single atomic operation: only applies when the stored version is lower than the incoming
        // version. Concurrent events for the same notification and out-of-order redelivery both
        // converge to the highest applied version without a read-then-write race.
        Update update = new Update()
            .setOnInsert("_id", aggregateId)
            .set("aggregateVersion", incomingVersion)
            .set("lastUpdated", lastUpdated);

        if (referenceNumber != null) update.set("referenceNumber", referenceNumber);
        if (status != null) update.set("status", status);
        if (originCountry != null) update.set("originCountry", originCountry);
        if (arrivalDate != null) update.set("arrivalDate", arrivalDate);
        if (commodity != null) update.set("commodity", commodity);

        Query query = Query.query(
            Criteria.where("_id").is(aggregateId)
                .and("aggregateVersion").lt(incomingVersion));

        mongoTemplate.upsert(query, update, AggregatedNotification.class);

        log.info("Upserted aggregatedNotification: aggregateId={}, aggregateVersion={}", aggregateId, incomingVersion);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
