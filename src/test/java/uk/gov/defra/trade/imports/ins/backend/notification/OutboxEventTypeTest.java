package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutboxEventTypeTest {

    @Test
    void fromWireValue_returnsNotificationEdited_forCorrectWireValue() {
        assertThat(OutboxEventType.fromWireValue("uk.gov.defra.imports.notification.NotificationEdited"))
            .contains(OutboxEventType.NOTIFICATION_EDITED);
    }

    @Test
    void fromWireValue_returnsNotificationSubmitted_forCorrectWireValue() {
        assertThat(OutboxEventType.fromWireValue("uk.gov.defra.imports.notification.NotificationSubmitted"))
            .contains(OutboxEventType.NOTIFICATION_SUBMITTED);
    }

    @Test
    void fromWireValue_returnsNotificationSubmissionAmended_forCorrectWireValue() {
        assertThat(OutboxEventType.fromWireValue("uk.gov.defra.imports.notification.NotificationSubmissionAmended"))
            .contains(OutboxEventType.NOTIFICATION_SUBMISSION_AMENDED);
    }

    @Test
    void fromWireValue_returnsEmpty_forUnknownWireValue() {
        assertThat(OutboxEventType.fromWireValue("uk.gov.defra.imports.notification.UnknownEvent"))
            .isEmpty();
    }

    @Test
    void fromWireValue_returnsEmpty_forNull() {
        assertThat(OutboxEventType.fromWireValue(null)).isEmpty();
    }
}
