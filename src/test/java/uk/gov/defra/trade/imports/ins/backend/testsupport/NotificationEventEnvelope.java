package uk.gov.defra.trade.imports.ins.backend.testsupport;

/**
 * Builds notification outbox JSON bodies for unit and integration tests.
 */
public final class NotificationEventEnvelope {

    public static final String DEFAULT_AGGREGATE_ID = "agg-1";
    public static final String DEFAULT_ORGANISATION_ID = "5900001";
    public static final String NOTIFICATION_EDITED =
        "uk.gov.defra.imports.notification.NotificationEdited";
    public static final String NOTIFICATION_SUBMITTED =
        "uk.gov.defra.imports.notification.NotificationSubmitted";

    private NotificationEventEnvelope() {}

    public static String headerOnly(
        String aggregateId, long aggregateVersion, String organisationId, String eventType) {
        return """
            {
              "aggregateId": "%s",
              "aggregateVersion": %d,
              "organisationId": "%s",
              "eventType": "%s"
            }
            """
            .formatted(aggregateId, aggregateVersion, organisationId, eventType);
    }

    public static String headerOnly(String aggregateId, long aggregateVersion, String organisationId) {
        return """
            {"aggregateId":"%s","aggregateVersion":%d,"organisationId":"%s"}
            """
            .formatted(aggregateId, aggregateVersion, organisationId);
    }

    public static String withoutOrganisation(String aggregateId, long aggregateVersion, String eventType) {
        return """
            {"aggregateId":"%s","aggregateVersion":%d,"eventType":"%s"}
            """
            .formatted(aggregateId, aggregateVersion, eventType);
    }

    public static String withData(
        String aggregateId,
        long aggregateVersion,
        String organisationId,
        String eventType,
        String dataObjectJson) {
        return """
            {
              "aggregateId": "%s",
              "aggregateVersion": %d,
              "organisationId": "%s",
              "eventType": "%s",
              "data": %s
            }
            """
            .formatted(aggregateId, aggregateVersion, organisationId, eventType, dataObjectJson);
    }
}