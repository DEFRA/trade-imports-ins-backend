package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotificationRepository;
import uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope;

class NotificationSqsListenerIT extends IntegrationBase {

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    @Autowired
    private AggregatedNotificationRepository repository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        purgeQueue();
        repository.deleteAll();
    }

    @Test
    void notificationEdited_createsDocumentInMongo() {
        sendToSqs(notificationEdited(1), AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AggregatedNotification> doc = repository.findById(AGGREGATE_ID);
            assertThat(doc).isPresent();
            assertThat(doc.get().getReferenceNumber()).isEqualTo("GBN-AG-26-001");
            assertThat(doc.get().getStatus()).isEqualTo("DRAFT");
            assertThat(doc.get().getOriginCountry()).isEqualTo("GB");
            assertThat(doc.get().getCommodity()).isEqualTo("Dog");
            assertThat(doc.get().getAggregateVersion()).isEqualTo(1L);
            assertThat(doc.get().getOrganisationId()).isEqualTo(ORGANISATION_ID);
            assertThat(doc.get().getArrivalDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        });
    }

    @Test
    void notificationEdited_storesTheArrivalDateAsAnIsoDateString_andLastUpdatedAsADate() {
        sendToSqs(notificationEdited(1), AGGREGATE_ID);

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
        String body = NotificationEventEnvelope.withData(
            AGGREGATE_ID,
            1,
            ORGANISATION_ID,
            NotificationEventEnvelope.NOTIFICATION_SUBMITTED,
            SUBDIVISION_ORIGIN_DATA);

        sendToSqs(body, AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AggregatedNotification> doc = repository.findById(AGGREGATE_ID);
            assertThat(doc).isPresent();
            assertThat(doc.get().getOriginCountry()).isEqualTo("ES");
            assertThat(doc.get().getOriginCountrySubdivisionCode()).isEqualTo("ES-CN");
        });
    }

    @Test
    void amendmentToAWholeCountryOrigin_clearsTheSubdivisionCode() {
        String subdivisionOrigin = NotificationEventEnvelope.withData(
            AGGREGATE_ID,
            1,
            ORGANISATION_ID,
            NotificationEventEnvelope.NOTIFICATION_SUBMITTED,
            """
            {
              "specifiedConsignment": {
                "originCountry": {
                  "code": { "value": "ES" },
                  "subordinateTradeCountrySubDivision": [
                    { "identifier": "ES-CN", "functionTypeCode": { "content": "106" } }
                  ]
                }
              }
            }
            """);
        sendToSqs(subdivisionOrigin, AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID))
                .hasValueSatisfying(doc -> assertThat(doc.getOriginCountrySubdivisionCode()).isEqualTo("ES-CN")));

        sendToSqs(notificationEdited(2), AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).hasValueSatisfying(doc -> {
                assertThat(doc.getOriginCountry()).isEqualTo("GB");
                assertThat(doc.getOriginCountrySubdivisionCode()).isNull();
            }));
    }

    @Test
    void deliveringSameEventTwice_leavesTheStoredDocumentUnchanged() {
        String eventWithNoIssueDateTime = NotificationEventEnvelope.withData(
            AGGREGATE_ID,
            1,
            ORGANISATION_ID,
            NotificationEventEnvelope.NOTIFICATION_EDITED,
            """
            {
              "exchangedDocument": {
                "identifier": "GBN-AG-26-001",
                "notificationStatusCode": "DRAFT"
              }
            }
            """);
        sendToSqs(eventWithNoIssueDateTime, AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(storedDocument()).isNotNull());
        Document storedAfterFirstDelivery = storedDocument();

        sendToSqs(eventWithNoIssueDateTime, AGGREGATE_ID);
        awaitQueueEmpty();

        assertThat(storedDocument()).isEqualTo(storedAfterFirstDelivery);
    }

    @Test
    void lowerAggregateVersion_isIgnored_documentKeepsHigherVersion() {
        sendToSqs(notificationEdited(5), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).isPresent());

        sendToSqs(notificationEdited(3), AGGREGATE_ID);

        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getAggregateVersion))
                .hasValue(5L));
    }

    @Test
    void lowerAggregateVersion_isCountedAsSkipped_notDiscarded() {
        sendToSqs(notificationEdited(5), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).isPresent());
        double skippedBefore = messageCount("skipped");
        double discardedBefore = messageCount("discarded");

        sendToSqs(notificationEdited(3), AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(messageCount("skipped")).isEqualTo(skippedBefore + 1));
        assertThat(messageCount("discarded")).isEqualTo(discardedBefore);
    }

    @Test
    void deliveringSameEventTwice_countsTheSecondDeliveryAsSkipped_notDiscarded() {
        sendToSqs(notificationEdited(1), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID)).isPresent());
        double skippedBefore = messageCount("skipped");
        double discardedBefore = messageCount("discarded");

        sendToSqs(notificationEdited(1), AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(messageCount("skipped")).isEqualTo(skippedBefore + 1));
        assertThat(messageCount("discarded")).isEqualTo(discardedBefore);
    }

    @Test
    void notificationSubmitted_updatesStatus_andKeepsTheStoredCommodity() {
        sendToSqs(notificationEdited(1), AGGREGATE_ID);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getStatus))
                .hasValue("DRAFT"));

        sendToSqs(notificationSubmittedWithNoCommodity(), AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getStatus))
                .hasValue("SUBMITTED"));
        assertThat(repository.findById(AGGREGATE_ID).map(AggregatedNotification::getCommodity))
            .hasValue("Dog");
    }

    @Test
    void unknownEventType_isDeletedWithoutBeingStored() {
        String body = NotificationEventEnvelope.withoutOrganisation(
            AGGREGATE_ID, 1, "uk.gov.defra.imports.notification.UnknownEvent");

        sendToSqs(body, AGGREGATE_ID);

        awaitQueueEmpty();
        assertThat(repository.findById(AGGREGATE_ID)).isEmpty();
    }

    @Test
    void invalidJson_isDeletedWithoutBeingStored() {
        sendToSqs("not valid json {{{", AGGREGATE_ID);

        awaitQueueEmpty();
        assertThat(repository.count()).isZero();
    }

    private double messageCount(String outcome) {
        return meterRegistry.counter("notification.ins.sqs.messages", "outcome", outcome).count();
    }

    private Document storedDocument() {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(AggregatedNotification.class))
            .find(new Document("_id", AGGREGATE_ID))
            .first();
    }

    private static String notificationEdited(long version) {
        return NotificationEventEnvelope.withData(
            AGGREGATE_ID,
            version,
            ORGANISATION_ID,
            NotificationEventEnvelope.NOTIFICATION_EDITED,
            NOTIFICATION_EDITED_DATA);
    }

    private static String notificationSubmittedWithNoCommodity() {
        return NotificationEventEnvelope.withData(
            AGGREGATE_ID,
            2,
            ORGANISATION_ID,
            NotificationEventEnvelope.NOTIFICATION_SUBMITTED,
            """
            {
              "exchangedDocument": {
                "identifier": "GBN-AG-26-001",
                "notificationStatusCode": "SUBMITTED",
                "issueDateTime": "2026-08-19T11:00:00Z"
              },
              "specifiedConsignment": {
                "originCountry": { "code": { "value": "GB" } }
              }
            }
            """);
    }

    private static final String NOTIFICATION_EDITED_DATA = """
        {
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
        """;

    private static final String SUBDIVISION_ORIGIN_DATA = """
        {
          "exchangedDocument": {
            "identifier": "GBN-AG-26-001",
            "notificationStatusCode": "SUBMITTED"
          },
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
        """;
}