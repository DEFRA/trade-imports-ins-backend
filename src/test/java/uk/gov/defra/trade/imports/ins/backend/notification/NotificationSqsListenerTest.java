package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    @Mock
    private NotificationUpsertService upsertService;

    private SimpleMeterRegistry registry;
    private NotificationSqsListener listener;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        listener = new NotificationSqsListener(new ObjectMapper(), upsertService, registry);
    }

    @Test
    void receive_delegatesToUpsertService_forKnownEventType() {
        listener.receive(notificationEdited(), "agg-1", "1");
        verify(upsertService).upsert(any());
        assertThat(registry.counter("notification.ins.sqs.messages", "outcome", "processed").count()).isEqualTo(1);
    }

    @Test
    void receive_throwsNonRetryable_forUnknownEventType() {
        String body = """
            {"aggregateId":"agg-1","aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.UnknownEvent"}
            """;
        assertThatThrownBy(() -> listener.receive(body, "agg-1", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Unrecognised eventType");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forBlankBody() {
        assertThatThrownBy(() -> listener.receive("", "agg-1", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Empty message body");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forNullBody() {
        assertThatThrownBy(() -> listener.receive(null, "agg-1", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Empty message body");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not json {{", "agg-1", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forBlankAggregateId() {
        assertThatThrownBy(() -> listener.receive(notificationEdited(), "", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forNullAggregateId() {
        assertThatThrownBy(() -> listener.receive(notificationEdited(), null, "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_delegatesToUpsertService_forNotificationCreated() {
        listener.receive(eventBody("NotificationCreated"), "agg-1", "1");
        verify(upsertService).upsert(any());
    }

    @Test
    void receive_delegatesToUpsertService_forNotificationAmendmentRequested() {
        listener.receive(eventBody("NotificationAmendmentRequested"), "agg-1", "1");
        verify(upsertService).upsert(any());
    }

    @Test
    void receive_delegatesToUpsertService_forNotificationAmendmentCancelled() {
        listener.receive(eventBody("NotificationAmendmentCancelled"), "agg-1", "1");
        verify(upsertService).upsert(any());
    }

    @Test
    void receive_delegatesToUpsertService_forNotificationDeleted() {
        listener.receive(eventBody("NotificationDeleted"), "agg-1", "1");
        verify(upsertService).upsert(any());
    }

    @Test
    void receive_delegatesToUpsertService_forNotificationSubmissionDeleted() {
        listener.receive(eventBody("NotificationSubmissionDeleted"), "agg-1", "1");
        verify(upsertService).upsert(any());
    }

    private static String notificationEdited() {
        return eventBody("NotificationEdited");
    }

    private static String eventBody(String eventName) {
        return """
            {"aggregateId":"%s","aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.%s"}
            """.formatted("agg-1", eventName);
    }
}
