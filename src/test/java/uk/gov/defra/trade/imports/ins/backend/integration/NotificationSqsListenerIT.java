package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotificationRepository;

class NotificationSqsListenerIT extends IntegrationBase {

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    @Autowired
    private AggregatedNotificationRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

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
            assertThat(doc.get().getCommodity()).isEqualTo("Dog");
            assertThat(doc.get().getAggregateVersion()).isEqualTo(1L);
            assertThat(doc.get().getArrivalDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        });
    }

    @Test
    void notificationEdited_storesTheArrivalDateAsAnIsoDateString_andLastUpdatedAsADate() {
        // Given
        sendToSqs(notificationEdited(1), AGGREGATE_ID);

        // When / Then
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Document stored = storedDocument();
            assertThat(stored).isNotNull();
            assertThat(stored.get("arrivalDate")).isEqualTo("2026-08-20");
            assertThat(stored.get("lastUpdated", Date.class).toInstant())
                .isEqualTo(Instant.parse("2026-08-19T10:00:00Z"));
        });
    }

    @Test
    void notificationFromACountrySubdivision_storesTheSubdivisionCode() {
        // Given — a Canary Islands origin, as the backend publishes it
        String body = """
            {
              "aggregateId": "%s",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "data": {
                "exchangedDocument": { "identifier": "GBN-AG-26-001", "notificationStatusCode": "SUBMITTED" },
                "specifiedConsignment": {
                  "originCountry": {
                    "code": { "value": "ES" },
                    "subordinateTradeCountrySubDivision": [
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
            """.formatted(AGGREGATE_ID);

        // When
        sendToSqs(body, AGGREGATE_ID);

        // Then
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AggregatedNotification> doc = repository.findById(AGGREGATE_ID);
            assertThat(doc).isPresent();
            assertThat(doc.get().getOriginCountry()).isEqualTo("ES");
            assertThat(doc.get().getOriginCountrySubdivisionCode()).isEqualTo("ES-CN");
        });
    }

    @Test
    void amendmentToAWholeCountryOrigin_clearsTheSubdivisionCode() {
        // Given — a Canary Islands origin is stored
        String subdivisionOrigin = """
            {
              "aggregateId": "%s",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "data": {
                "specifiedConsignment": {
                  "originCountry": {
                    "code": { "value": "ES" },
                    "subordinateTradeCountrySubDivision": [
                      { "identifier": "ES-CN", "functionTypeCode": { "content": "106" } }
                    ]
                  }
                }
              }
            }
            """.formatted(AGGREGATE_ID);
        sendToSqs(subdivisionOrigin, AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID))
                .hasValueSatisfying(doc -> assertThat(doc.getOriginCountrySubdivisionCode()).isEqualTo("ES-CN")));

        // When — a later version moves the origin to a whole country
        sendToSqs(notificationEdited(2), AGGREGATE_ID);

        // Then
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).hasValueSatisfying(doc -> {
                assertThat(doc.getOriginCountry()).isEqualTo("GB");
                assertThat(doc.getOriginCountrySubdivisionCode()).isNull();
            }));
    }

    @Test
    void deliveringSameEventTwice_leavesTheStoredDocumentUnchanged() {
        // Given
        String eventWithNoIssueDateTime = """
            {
              "aggregateId": "%s",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationEdited",
              "data": {
                "exchangedDocument": { "identifier": "GBN-AG-26-001", "notificationStatusCode": "DRAFT" }
              }
            }
            """.formatted(AGGREGATE_ID);
        sendToSqs(eventWithNoIssueDateTime, AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(storedDocument()).isNotNull());
        Document storedAfterFirstDelivery = storedDocument();

        // When
        sendToSqs(eventWithNoIssueDateTime, AGGREGATE_ID);
        awaitQueueEmpty();

        // Then
        assertThat(storedDocument()).isEqualTo(storedAfterFirstDelivery);
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

        // When — submit event arrives, carrying no includedConsignmentItem
        sendToSqs(notificationSubmitted(), AGGREGATE_ID);

        // Then — status must update to SUBMITTED, and the previously stored commodity survives
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getStatus))
                .hasValue("SUBMITTED"));
        assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getCommodity))
            .hasValue("Dog");
    }

    @Test
    void unknownEventType_isDeletedWithoutBeingStored() {
        // Given
        String body = """
            {"aggregateId":"%s","aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.UnknownEvent"}
            """.formatted(AGGREGATE_ID);

        // When
        sendToSqs(body, AGGREGATE_ID);

        // Then
        awaitQueueEmpty();
        assertThat(repository.findById(AGGREGATE_ID)).isEmpty();
    }

    @Test
    void invalidJson_isDeletedWithoutBeingStored() {
        // Given / When
        sendToSqs("not valid json {{{", AGGREGATE_ID);

        // Then
        awaitQueueEmpty();
        assertThat(repository.count()).isZero();
    }

    private Document storedDocument() {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(AggregatedNotification.class))
            .find(new Document("_id", AGGREGATE_ID))
            .first();
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
                        { "applicableClassification": [{ "classCode": { "value": "01059900" } }],
                          "commonName": "Dog" }
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
