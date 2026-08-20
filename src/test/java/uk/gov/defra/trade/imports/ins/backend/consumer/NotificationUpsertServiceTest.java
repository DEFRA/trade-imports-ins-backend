package uk.gov.defra.trade.imports.ins.backend.consumer;

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
import uk.gov.defra.trade.imports.ins.backend.store.AggregatedNotification;

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
        JsonNode body = mapper.readTree(fullNotificationEdited("agg-1", 3));

        service.upsert(body);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(AggregatedNotification.class));

        // Query must filter on _id AND aggregateVersion < incomingVersion
        Document queryDoc = queryCaptor.getValue().getQueryObject();
        assertThat(queryDoc.get("_id")).isEqualTo("agg-1");
        assertThat(queryDoc.containsKey("aggregateVersion")).isTrue();

        // Update must include the snapshot fields — inspect BSON document directly to avoid codec issues
        Document setFields = updateCaptor.getValue().getUpdateObject().get("$set", Document.class);
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

    private static String fullNotificationEdited(String aggregateId, long version) {
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
            """.formatted(aggregateId, version);
    }
}
