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
  private static final String ORIGIN_COUNTRY = "originCountry";
  // UNCL3227 location function 106 (the code list calls it "Region of origin"): here, the country
  // subdivision the goods came from. Not the region of origin code, which INS does not store.
  private static final String SUBDIVISION_OF_ORIGIN = "106";
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

        JsonNode originCountryNode = specifiedConsignment.path(ORIGIN_COUNTRY);
        String originCountry = textOrNull(originCountryNode.path("code").path("value"));

        String originCountrySubdivisionCode = countrySubdivisionCode(
            originCountryNode.path("subordinateTradeCountrySubDivision"));

        String arrivalDateStr = textOrNull(
            specifiedConsignment
                .path("mainCarriageLogisticsTransportMovement").path(0)
                .path("arrivalEvent").path(0)
                .path("scheduledOccurrenceDateTime"));

        String commodity = textOrNull(
            specifiedConsignment
                .path("includedConsignmentItem").path(0)
                .path("includedTradeLineItem").path(0)
                .path("commonName"));

        Instant lastUpdated = lastUpdatedStr != null
            ? parseInstant(lastUpdatedStr, "issueDateTime", aggregateId)
            : Instant.now();

        Instant arrivalDate = arrivalDateStr != null
            ? parseInstant(arrivalDateStr, "scheduledOccurrenceDateTime", aggregateId)
            : null;

        // Single atomic operation: only applies when the stored version is lower than the incoming
        // version. Concurrent events for the same notification and out-of-order redelivery both
        // converge to the highest applied version without a read-then-write race.
        Update update = new Update()
            .setOnInsert("_id", aggregateId)
            .set(AGGREGATE_VERSION, incomingVersion)
            .set("lastUpdated", lastUpdated);

        if (referenceNumber != null) update.set("referenceNumber", referenceNumber);
        if (status != null) update.set("status", status);
        if (originCountry != null) update.set(ORIGIN_COUNTRY, originCountry);
        // The subdivision belongs to the origin it arrived with. An event that carries an origin
        // with no subdivision (say, an amendment from the Canary Islands to mainland France) clears
        // it; an event with no origin at all, such as a lifecycle event, leaves it alone.
        if (originCountrySubdivisionCode != null) {
            update.set("originCountrySubdivisionCode", originCountrySubdivisionCode);
        } else if (originCountry != null) {
            update.unset("originCountrySubdivisionCode");
        }
        if (arrivalDate != null) update.set("arrivalDate", arrivalDate);
        if (commodity != null) update.set("commodity", commodity);

        Query query = Query.query(
            Criteria.where("_id").is(aggregateId)
                .and(AGGREGATE_VERSION).lt(incomingVersion));

        mongoTemplate.upsert(query, update, AggregatedNotification.class);

        log.info("Upserted aggregatedNotification: aggregateId={}, aggregateVersion={}", aggregateId, incomingVersion);
    }

    // The origin's subdivision is its '106' sub-division entry; a country can carry other
    // sub-divisions too, so the code is picked by function, not position.
    private static String countrySubdivisionCode(JsonNode subDivisions) {
        for (JsonNode subDivision : subDivisions) {
            if (SUBDIVISION_OF_ORIGIN.equals(subDivision.path("functionTypeCode").path("content").asText(null))) {
                return textOrNull(subDivision.path("identifier"));
            }
        }
        return null;
    }

    private static Instant parseInstant(String value, String fieldName, String aggregateId) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new SqsNonRetryableException(
                "Invalid " + fieldName + " for aggregateId=" + aggregateId, e);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
