package uk.gov.defra.trade.imports.ins.backend.consumer;

import java.util.Optional;

public enum OutboxEventType {

    NOTIFICATION_EDITED("uk.gov.defra.imports.notification.NotificationEdited"),
    NOTIFICATION_SUBMITTED("uk.gov.defra.imports.notification.NotificationSubmitted"),
    NOTIFICATION_SUBMISSION_AMENDED("uk.gov.defra.imports.notification.NotificationSubmissionAmended"),
    NOTIFICATION_CREATED("uk.gov.defra.imports.notification.NotificationCreated"),
    NOTIFICATION_SUBMISSION_CANCELLED("uk.gov.defra.imports.notification.NotificationSubmissionCancelled");

    private final String wireValue;

    OutboxEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public static Optional<OutboxEventType> fromWireValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (OutboxEventType type : values()) {
            if (type.wireValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
