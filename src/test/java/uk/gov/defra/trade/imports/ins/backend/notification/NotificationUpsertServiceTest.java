package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension.EAST_OF_UTC;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension.WEST_OF_UTC;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.defra.trade.imports.ins.backend.testsupport.JvmDefaultZoneExtension;

@ExtendWith(MockitoExtension.class)
class NotificationUpsertServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private NotificationUpsertService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @RegisterExtension
    final JvmDefaultZoneExtension jvmDefaultZone = new JvmDefaultZoneExtension();

    @BeforeEach
    void setup() {
        service = new NotificationUpsertService(mongoTemplate);
    }

    @Test
    void upsert_shouldUpsertTheSnapshot_onlyOverAnOlderStoredVersion() throws Exception {
        JsonNode body = mapper.readTree(fullNotificationEdited(3));

        service.upsert(body);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), any(Update.class), eq(AggregatedNotification.class));

        Document queryDoc = queryCaptor.getValue().getQueryObject();
        assertThat(queryDoc).containsEntry("_id", "agg-1");
        Document versionFilter = queryDoc.get("aggregateVersion", Document.class);
        assertThat(versionFilter).containsEntry("$lt", 3L);

        Document updateDoc = upsertedUpdate();
        Document setOnInsert = updateDoc.get("$setOnInsert", Document.class);
        assertThat(setOnInsert).containsEntry("_id", "agg-1");
        Document setFields = updateDoc.get("$set", Document.class);
        assertThat(setFields).containsEntry("referenceNumber", "GBN-AG-26-001")
            .containsEntry("status", "DRAFT")
            .containsEntry("originCountry", "GB");
    }

    @Test
    void upsert_shouldRejectAsNonRetryable_whenAggregateIdIsMissing() throws Exception {
        JsonNode body = mapper.readTree("""
            {"aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.NotificationEdited"}
            """);
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("aggregateId");
    }

    @Test
    void upsert_shouldRejectAsNonRetryable_whenAggregateVersionIsMissing() throws Exception {
        JsonNode body = mapper.readTree("""
            {"aggregateId":"agg-1","eventType":"uk.gov.defra.imports.notification.NotificationEdited"}
            """);
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("aggregateVersion");
    }

    @Test
    void upsert_shouldRejectAsNonRetryable_whenIssueDateTimeIsNotADate() throws Exception {
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

        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("issueDateTime")
            .hasMessageContaining("agg-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {WEST_OF_UTC, EAST_OF_UTC})
    void upsert_shouldSetTheUtcCalendarDayOfTheScheduledArrival_whateverTheJvmDefaultZone(
        String zoneId) throws Exception {
        jvmDefaultZone.setJvmDefaultZone(zoneId);
        JsonNode body = mapper.readTree(eventScheduledToArriveAt("2026-07-21T00:00:00Z"));

        service.upsert(body);

        Document setFields = upsertedUpdate().get("$set", Document.class);
        assertThat(setFields).containsEntry("arrivalDate", LocalDate.of(2026, 7, 21));
    }

    @Test
    void upsert_shouldRejectAsNonRetryable_whenScheduledOccurrenceDateTimeHasNoTime() throws Exception {
        JsonNode body = mapper.readTree(eventScheduledToArriveAt("2026-07-21"));

        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("scheduledOccurrenceDateTime")
            .hasMessageContaining("agg-1");
    }

    @Test
    void upsert_shouldLeaveStaleEventsToTheVersionFilter_ratherThanSkippingThemItself() throws Exception {
        JsonNode body = mapper.readTree(fullNotificationEdited(1));

        service.upsert(body);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), any(Update.class), eq(AggregatedNotification.class));
        Document versionFilter = queryCaptor.getValue().getQueryObject().get("aggregateVersion", Document.class);
        assertThat(versionFilter).containsEntry("$lt", 1L);
    }

    @Test
    void upsert_shouldLeaveReferenceStatusAndSubdivisionUnset_andClearNothing_whenTheEventHasNoData() throws Exception {
        JsonNode body = mapper.readTree("""
            {"aggregateId":"agg-1","aggregateVersion":2}
            """);

        service.upsert(body);

        Document updateDoc = upsertedUpdate();
        Document setFields = updateDoc.get("$set", Document.class);
        assertThat(setFields).doesNotContainKey("referenceNumber")
            .doesNotContainKey("status")
            .doesNotContainKey("originCountrySubdivisionCode");
        assertThat(updateDoc.get("$unset", Document.class)).isNull();
    }

    @Test
    void upsert_shouldStoreTheCountrySubdivisionCode_andNotTheRegionOfOriginCode() throws Exception {
        JsonNode body = mapper.readTree(eventFromTheCanaryIslandsWithAnotherSubDivisionListedFirst());

        service.upsert(body);

        Document setFields = upsertedUpdate().get("$set", Document.class);
        assertThat(setFields).containsEntry("originCountry", "ES")
            .containsEntry("originCountrySubdivisionCode", "ES-CN")
            .doesNotContainValue("ES-12345");
    }

    @Test
    void upsert_shouldClearSubdivisionCode_whenOriginIsAWholeCountry() throws Exception {
        JsonNode body = mapper.readTree(fullNotificationEdited(1));

        service.upsert(body);

        Document updateDoc = upsertedUpdate();
        Document setFields = updateDoc.get("$set", Document.class);
        Document unsetFields = updateDoc.get("$unset", Document.class);
        assertThat(setFields).containsEntry("originCountry", "GB")
            .doesNotContainKey("originCountrySubdivisionCode");
        assertThat(unsetFields).containsKey("originCountrySubdivisionCode");
    }

    private Document upsertedUpdate() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(AggregatedNotification.class));
        return updateCaptor.getValue().getUpdateObject();
    }

    private static String eventScheduledToArriveAt(String scheduledOccurrenceDateTime) {
        return """
            {
              "aggregateId": "agg-1",
              "aggregateVersion": 1,
              "data": {
                "specifiedConsignment": {
                  "mainCarriageLogisticsTransportMovement": [
                    { "arrivalEvent": [{ "scheduledOccurrenceDateTime": "%s" }] }
                  ]
                }
              }
            }
            """.formatted(scheduledOccurrenceDateTime);
    }

    private static String eventFromTheCanaryIslandsWithAnotherSubDivisionListedFirst() {
        return """
            {
              "aggregateId": "agg-1",
              "aggregateVersion": 1,
              "data": {
                "specifiedConsignment": {
                  "originCountry": {
                    "code": { "value": "ES" },
                    "subordinateTradeCountrySubDivision": [
                      { "functionTypeCode": { "content": "44" } },
                      {
                        "identifier": "ES-CN",
                        "urlId": "https://refdata.tbc.defra.gov.uk/country_subdivisions",
                        "functionTypeCode": { "content": "106" }
                      }
                    ],
                    "regionOfOriginCode": "ES-12345"
                  }
                }
              }
            }
            """;
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
