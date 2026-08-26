package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotificationRepository;

class NotificationSqsListenerIT extends IntegrationBase {

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    @Autowired
    private AggregatedNotificationRepository repository;

    @BeforeEach
    void setup() {
        purgeQueue();
        repository.deleteAll();
    }

    @Test
    void notificationEdited_createsDocumentInMongo() {
        // Given
        sendToSqs(notificationEdited(1), AGGREGATE_ID);

        // When / Then
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AggregatedNotification> doc = repository.findById(AGGREGATE_ID);
            assertThat(doc).isPresent();
            assertThat(doc.get().getReferenceNumber()).isEqualTo("GBN-AG-26-001");
            assertThat(doc.get().getStatus()).isEqualTo("DRAFT");
            assertThat(doc.get().getOriginCountry()).isEqualTo("GB");
            assertThat(doc.get().getCommodity()).isNull();
            assertThat(doc.get().getAggregateVersion()).isEqualTo(1L);
        });
    }

    @Test
    void deliveringSameEventTwice_isIdempotent() {
        // Given
        String body = notificationEdited(1);
        sendToSqs(body, AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).isPresent());

        // When
        sendToSqs(body, AGGREGATE_ID);

        // Then — version guard means second delivery is a no-op
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(repository.count()).isEqualTo(1L));
    }

    @Test
    void lowerAggregateVersion_isIgnored_documentKeepsHigherVersion() {
        // Given
        sendToSqs(notificationEdited(5), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).isPresent());

        // When
        sendToSqs(notificationEdited(3), AGGREGATE_ID);

        // Then
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getAggregateVersion))
                .hasValue(5L));
    }

    @Test
    void lifecycleEvent_notificationSubmitted_updatesStatus() {
        // Given — prime the store with a DRAFT from a NotificationEdited
        sendToSqs(notificationEdited(1), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getStatus))
                .hasValue("DRAFT"));

        // When — submit event arrives
        sendToSqs(notificationSubmitted(), AGGREGATE_ID);

        // Then — status must update to SUBMITTED
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getStatus))
                .hasValue("SUBMITTED"));
    }

    @Test
    void unknownEventType_isDeadLettered_notRemainingInQueue() {
        // Given
        String body = """
            {"aggregateId":"%s","aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.UnknownEvent"}
            """.formatted(AGGREGATE_ID);

        // When
        sendToSqs(body, AGGREGATE_ID);

        // Then — non-retryable: message must be deleted from the queue (not redelivered)
        awaitQueueEmpty();
        assertThat(repository.findById(AGGREGATE_ID)).isEmpty();
    }

    @Test
    void invalidJson_isDeadLettered() {
        // Given / When
        sendToSqs("not valid json {{{", AGGREGATE_ID);

        // Then — non-retryable: message must be deleted from the queue (not redelivered)
        awaitQueueEmpty();
        assertThat(repository.count()).isZero();
    }

    private static String notificationEdited(long version) {
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
            """.formatted(NotificationSqsListenerIT.AGGREGATE_ID, version);
    }

    private static String notificationSubmitted() {
        return """
            {
              "aggregateId": "%s",
              "aggregateVersion": %d,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "data": {
                "exchangedDocument": {
                  "identifier": "GBN-AG-26-001",
                  "notificationStatusCode": "SUBMITTED",
                  "issueDateTime": "2026-08-19T11:00:00Z"
                },
                "specifiedConsignment": {
                  "originCountry": { "code": { "value": "GB" } }
                }
              }
            }
            """.formatted(NotificationSqsListenerIT.AGGREGATE_ID, (long) 2);
    }
}
