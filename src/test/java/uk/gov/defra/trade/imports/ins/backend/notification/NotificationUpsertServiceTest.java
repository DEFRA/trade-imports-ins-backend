package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class NotificationUpsertServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private NotificationUpsertService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = new NotificationUpsertService(mongoTemplate);
    }

    @Test
    void upsert_callsMongoTemplate_withVersionGuardedQuery() throws Exception {
        JsonNode body = mapper.readTree(fullNotificationEdited(3));

        service.upsert(body);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(AggregatedNotification.class));

        // Query must filter on _id AND aggregateVersion < incomingVersion
        Document queryDoc = queryCaptor.getValue().getQueryObject();
        assertThat(queryDoc.get("_id")).isEqualTo("agg-1");
        Document versionFilter = queryDoc.get("aggregateVersion", Document.class);
        assertThat(versionFilter.get("$lt")).isEqualTo(3L);

        // Update must include the snapshot fields — inspect BSON document directly to avoid codec issues
        Document updateDoc = updateCaptor.getValue().getUpdateObject();
        Document setOnInsert = updateDoc.get("$setOnInsert", Document.class);
        assertThat(setOnInsert.get("_id")).isEqualTo("agg-1");
        Document setFields = updateDoc.get("$set", Document.class);
        assertThat(setFields.get("referenceNumber")).isEqualTo("GBN-AG-26-001");
        assertThat(setFields.get("status")).isEqualTo("DRAFT");
        assertThat(setFields.get("originCountry")).isEqualTo("GB");
    }

    @Test
    void upsert_throwsNonRetryable_whenAggregateIdMissing() throws Exception {
        JsonNode body = mapper.readTree("""
            {"aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.NotificationEdited"}
            """);
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("aggregateId");
    }

    @Test
    void upsert_throwsNonRetryable_whenAggregateVersionMissing() throws Exception {
        JsonNode body = mapper.readTree("""
            {"aggregateId":"agg-1","eventType":"uk.gov.defra.imports.notification.NotificationEdited"}
            """);
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("aggregateVersion");
    }

    @Test
    void upsert_throwsNonRetryable_whenIssueDateTimeIsMalformed() throws Exception {
        // Given
        JsonNode body = mapper.readTree("""
            {
              "aggregateId": "agg-1",
              "aggregateVersion": 1,
              "data": {
                "exchangedDocument": {
                  "issueDateTime": "not-a-date"
                }
              }
            }
            """);

        // When / Then
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("issueDateTime")
            .hasMessageContaining("agg-1");
    }

    @Test
    void upsert_delegatesVersionFilterToMongo_forStaleEvent() throws Exception {
        // Given — version 1 would be stale if the store already holds version 3, but the service
        // does not check staleness itself; it always issues the upsert and lets MongoDB match nothing.
        JsonNode body = mapper.readTree(fullNotificationEdited(1));

        // When
        service.upsert(body);

        // Then — the query encodes $lt: 1 so MongoDB will match no document and apply no write
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), any(Update.class), eq(AggregatedNotification.class));
        Document versionFilter = queryCaptor.getValue().getQueryObject().get("aggregateVersion", Document.class);
        assertThat(versionFilter.get("$lt")).isEqualTo(1L);
    }

    @Test
    void upsert_doesNotSetMissingFields() throws Exception {
        // Lifecycle event with no data node — version guard still runs, optional fields skipped
        JsonNode body = mapper.readTree("""
            {"aggregateId":"agg-1","aggregateVersion":2}
            """);

        service.upsert(body);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(AggregatedNotification.class));

        Document setFields = updateCaptor.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(setFields).doesNotContainKey("referenceNumber");
        assertThat(setFields).doesNotContainKey("status");
    }

    private static String fullNotificationEdited(long version) {
        return """
            {
              "aggregateId": "%s",
              "aggregateVersion": %d,
              "eventType": "uk.gov.defra.imports.notification.NotificationEdited",
              "data": {
                "exchangedDocument": {
                  "identifier": "GBN-AG-26-001",
                  "notificationStatusCode": "DRAFT",
                  "issueDateTime": "2026-08-19T10:00:00Z"
                },
                "specifiedConsignment": {
                  "originCountry": { "code": { "value": "GB" } },
                  "mainCarriageLogisticsTransportMovement": [
                    { "arrivalEvent": [{ "scheduledOccurrenceDateTime": "2026-08-20T00:00:00Z" }] }
                  ],
                  "includedConsignmentItem": [
                    { "includedTradeLineItem": [
                        { "applicableClassification": [{ "classCode": { "value": "01059900" } }] }
                    ]}
                  ]
                }
              }
            }
            """.formatted("agg-1", version);
    }
}
