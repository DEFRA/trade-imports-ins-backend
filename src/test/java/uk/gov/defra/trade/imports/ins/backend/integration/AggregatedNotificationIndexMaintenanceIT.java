package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotificationIndexMaintenance;

class AggregatedNotificationIndexMaintenanceIT extends IntegrationBase {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AggregatedNotificationIndexMaintenance indexMaintenance;

    @BeforeEach
    void setup() {
        mongoTemplate.dropCollection(AggregatedNotification.class);
    }

    @Test
    void startup_shouldDropLegacyStatusSortIndex_whenItExists() {
        IndexOperations indexOps = mongoTemplate.indexOps(AggregatedNotification.class);
        indexOps.createIndex(
            new Index()
                .on("status", Sort.Direction.ASC)
                .on("arrivalDate", Sort.Direction.DESC)
                .on("lastUpdated", Sort.Direction.DESC)
                .named(AggregatedNotificationIndexMaintenance.LEGACY_STATUS_SORT_INDEX));

        indexMaintenance.afterPropertiesSet();

        assertThat(indexOps.getIndexInfo())
            .extracting(IndexInfo::getName)
            .doesNotContain(AggregatedNotificationIndexMaintenance.LEGACY_STATUS_SORT_INDEX);
    }

    @Test
    void startup_shouldDeleteNotificationsWithoutOrganisationId() {
        mongoTemplate.save(
            AggregatedNotification.builder()
                .aggregateId("missing-org")
                .aggregateVersion(1L)
                .referenceNumber("GBN-AG-26-001")
                .status("DRAFT")
                .lastUpdated(Instant.parse("2026-08-19T10:00:00Z"))
                .build());
        mongoTemplate.save(
            AggregatedNotification.builder()
                .aggregateId("with-org")
                .aggregateVersion(1L)
                .organisationId(ORGANISATION_ID)
                .referenceNumber("GBN-AG-26-002")
                .status("DRAFT")
                .arrivalDate(LocalDate.of(2026, 8, 1))
                .lastUpdated(Instant.parse("2026-08-19T10:00:00Z"))
                .build());
        mongoTemplate.save(
            AggregatedNotification.builder()
                .aggregateId("empty-org")
                .aggregateVersion(1L)
                .organisationId("")
                .referenceNumber("GBN-AG-26-003")
                .status("DRAFT")
                .lastUpdated(Instant.parse("2026-08-19T10:00:00Z"))
                .build());

        indexMaintenance.afterPropertiesSet();

        assertThat(mongoTemplate.findAll(AggregatedNotification.class))
            .extracting(AggregatedNotification::getAggregateId)
            .containsExactly("with-org");
    }
}