package uk.gov.defra.trade.imports.ins.backend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Drops the legacy {@code status_sort_fields} index so Spring Data can create
 * {@link AggregatedNotification#ORGANISATION_STATUS_SORT_INDEX} on existing databases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatedNotificationIndexMaintenance implements InitializingBean {

    public static final String LEGACY_STATUS_SORT_INDEX = "status_sort_fields";

    private final MongoTemplate mongoTemplate;

    @Value("${notification.delete-missing-organisation-id-at-startup:true}")
    private boolean deleteMissingOrganisationIdAtStartup;

    @Override
    public void afterPropertiesSet() {
        IndexOperations indexOps = mongoTemplate.indexOps(AggregatedNotification.class);
        try {
            indexOps.dropIndex(LEGACY_STATUS_SORT_INDEX);
            log.info("Dropped legacy Mongo index {}", LEGACY_STATUS_SORT_INDEX);
        } catch (RuntimeException exception) {
            log.debug("No legacy index {} to drop", LEGACY_STATUS_SORT_INDEX, exception);
        }

        if (deleteMissingOrganisationIdAtStartup) {
            long removed = deleteNotificationsWithoutOrganisationId();
            if (removed > 0) {
                log.info(
                    "Removed {} aggregated notifications with no {} at startup",
                    removed,
                    AggregatedNotification.ORGANISATION_ID_FIELD);
            }
        }
    }

    long deleteNotificationsWithoutOrganisationId() {
        Query withoutOrganisationId =
            Query.query(
                new Criteria()
                    .orOperator(
                        Criteria.where(AggregatedNotification.ORGANISATION_ID_FIELD).isNull(),
                        Criteria.where(AggregatedNotification.ORGANISATION_ID_FIELD).is("")));
        return mongoTemplate
            .remove(withoutOrganisationId, AggregatedNotification.class)
            .getDeletedCount();
    }
}