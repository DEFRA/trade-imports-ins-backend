package uk.gov.defra.trade.imports.ins.backend.notification;

import java.util.Optional;

public enum OutboxEventType {

    NOTIFICATION_EDITED("uk.gov.defra.imports.notification.NotificationEdited"),
    NOTIFICATION_SUBMITTED("uk.gov.defra.imports.notification.NotificationSubmitted"),
    NOTIFICATION_SUBMISSION_AMENDED("uk.gov.defra.imports.notification.NotificationSubmissionAmended"),
    NOTIFICATION_CREATED("uk.gov.defra.imports.notification.NotificationCreated"),
    NOTIFICATION_AMENDMENT_REQUESTED("uk.gov.defra.imports.notification.NotificationAmendmentRequested"),
    NOTIFICATION_AMENDMENT_CANCELLED("uk.gov.defra.imports.notification.NotificationAmendmentCancelled"),
    NOTIFICATION_DELETED("uk.gov.defra.imports.notification.NotificationDeleted"),
    NOTIFICATION_SUBMISSION_DELETED("uk.gov.defra.imports.notification.NotificationSubmissionDeleted");

    private final String wireValue;

    OutboxEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
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
