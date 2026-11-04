package uk.gov.defra.trade.imports.ins.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
  private static final String LOCATION_FUNCTION_SUBDIVISION_OF_ORIGIN = "106";
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

        String referenceNumber = text(exchangedDocument.path("identifier"));
        String status = text(exchangedDocument.path("notificationStatusCode"));
        String lastUpdatedStr = text(exchangedDocument.path("issueDateTime"));

        JsonNode originCountryNode = specifiedConsignment.path(ORIGIN_COUNTRY);
        String originCountry = text(originCountryNode.path("code").path("value"));

        String originCountrySubdivisionCode = countrySubdivisionCode(
            originCountryNode.path("subordinateTradeCountrySubDivision"));

        String scheduledOccurrenceDateTime = text(
            specifiedConsignment
                .path("mainCarriageLogisticsTransportMovement").path(0)
                .path("arrivalEvent").path(0)
                .path("scheduledOccurrenceDateTime"));

        JsonNode firstTradeLine = specifiedConsignment
            .path("includedConsignmentItem").path(0)
            .path("includedTradeLineItem").path(0);
        String commodity = text(firstTradeLine.path("description").path(0));
        if (commodity == null) {
            commodity = text(firstTradeLine.path("commonName"));
            if (commodity != null) {
                log.info(
                    "No trade-line description, dashboard commodity taken from commonName: aggregateId={}, aggregateVersion={}",
                    aggregateId,
                    incomingVersion);
            }
        }

        Instant lastUpdated = lastUpdatedStr != null
            ? parseInstant(lastUpdatedStr, "issueDateTime", aggregateId)
            : Instant.now();

        LocalDate arrivalDate = scheduledOccurrenceDateTime != null
            ? parseInstant(scheduledOccurrenceDateTime, "scheduledOccurrenceDateTime", aggregateId)
                .atZone(ZoneOffset.UTC).toLocalDate()
            : null;

        Update update = new Update()
            .setOnInsert("_id", aggregateId)
            .set(AGGREGATE_VERSION, incomingVersion)
            .set("lastUpdated", lastUpdated);

        if (referenceNumber != null) update.set("referenceNumber", referenceNumber);
        if (status != null) update.set("status", status);
        if (originCountry != null) update.set(ORIGIN_COUNTRY, originCountry);
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

    private static String countrySubdivisionCode(JsonNode subDivisions) {
        for (JsonNode subDivision : subDivisions) {
            if (LOCATION_FUNCTION_SUBDIVISION_OF_ORIGIN.equals(subDivision.path("functionTypeCode").path("content").asText(null))) {
                return text(subDivision.path("identifier"));
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

    private static String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
