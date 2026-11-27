package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope.DEFAULT_AGGREGATE_ID;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope.DEFAULT_ORGANISATION_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.defra.trade.imports.ins.backend.testsupport.NotificationEventEnvelope;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();

    @Mock
    private MongoTemplate mongoTemplate;

    private SimpleMeterRegistry registry;
    private NotificationSqsListener listener;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        listener = new NotificationSqsListener(
            new ObjectMapper(), new NotificationUpsertService(mongoTemplate), new NotificationMessageMetrics(registry));
    }

    @Test
    void receive_storesTheNotification_andCountsItProcessed_forKnownEventType() {
        listener.receive(notificationEdited(), DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID);

        assertStoredVersionOneOfTheAggregate();
        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "processed").count()).isEqualTo(1);
    }

    @Test
    void receive_countsTheMessageAsSkipped_notProcessed_whenTheUpsertSkipsAStaleEvent() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(AggregatedNotification.class)))
            .thenThrow(new DuplicateKeyException("E11000 duplicate key error collection: notifications"));

        listener.receive(notificationEdited(), DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID);

        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "skipped").count()).isEqualTo(1);
        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "processed").count()).isZero();
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forUnknownEventType() {
        String body = eventBody("UnknownEvent");
        assertThatThrownBy(() -> listener.receive(body, DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Unrecognised eventType");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forBlankBody() {
        assertThatThrownBy(() -> listener.receive("", DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Empty message body");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forNullBody() {
        assertThatThrownBy(() -> listener.receive(null, DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Empty message body");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not json {{", DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forBlankAggregateId() {
        assertThatThrownBy(() -> listener.receive(notificationEdited(), "", "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void receive_throwsNonRetryable_andStoresNothing_forNullAggregateId() {
        assertThatThrownBy(() -> listener.receive(notificationEdited(), null, "1", null, MESSAGE_ID))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");
        verifyNoInteractions(mongoTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "NotificationCreated",
        "NotificationAmendmentRequested",
        "NotificationAmendmentCancelled",
        "NotificationDeleted",
        "NotificationSubmissionDeleted"
    })
    void receive_storesTheNotification_forEachLifecycleEventType(String eventName) {
        listener.receive(eventBody(eventName), DEFAULT_AGGREGATE_ID, "1", null, MESSAGE_ID);

        assertStoredVersionOneOfTheAggregate();
    }

    private void assertStoredVersionOneOfTheAggregate() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(AggregatedNotification.class));
        Document stored = updateCaptor.getValue().getUpdateObject();
        assertThat(stored.get("$setOnInsert", Document.class)).containsEntry("_id", DEFAULT_AGGREGATE_ID);
        assertThat(stored.get("$set", Document.class))
            .containsEntry("aggregateVersion", 1L)
            .containsEntry("organisationId", DEFAULT_ORGANISATION_ID);
    }

    private static String notificationEdited() {
        return eventBody("NotificationEdited");
    }

    private static String eventBody(String eventName) {
        return NotificationEventEnvelope.headerOnly(
            DEFAULT_AGGREGATE_ID, 1, DEFAULT_ORGANISATION_ID, "uk.gov.defra.imports.notification." + eventName);
    }
}
