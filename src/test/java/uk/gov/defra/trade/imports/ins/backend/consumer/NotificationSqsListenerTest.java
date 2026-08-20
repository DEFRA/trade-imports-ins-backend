package uk.gov.defra.trade.imports.ins.backend.consumer;

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

    private NotificationSqsListener listener;

    @BeforeEach
    void setup() {
        listener = new NotificationSqsListener(new ObjectMapper(), upsertService, new SimpleMeterRegistry());
    }

    @Test
    void receive_delegatesToUpsertService_forKnownEventType() {
        listener.receive(notificationEdited("agg-1"), "agg-1", "1");
        verify(upsertService).upsert(any());
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
    void receive_throwsNonRetryable_forInvalidJson() {
        assertThatThrownBy(() -> listener.receive("not json {{", "agg-1", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("not valid JSON");
        verify(upsertService, never()).upsert(any());
    }

    @Test
    void receive_throwsNonRetryable_forBlankAggregateId() {
        assertThatThrownBy(() -> listener.receive(notificationEdited("agg-1"), "", "1"))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("MESSAGE_GROUP_ID");
        verify(upsertService, never()).upsert(any());
    }

    private static String notificationEdited(String aggregateId) {
        return """
            {"aggregateId":"%s","aggregateVersion":1,"eventType":"uk.gov.defra.imports.notification.NotificationEdited"}
            """.formatted(aggregateId);
    }
}
