package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AggregatedNotificationIndexMaintenanceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    private AggregatedNotificationIndexMaintenance maintenance;

    @BeforeEach
    void setup() {
        when(mongoTemplate.indexOps(AggregatedNotification.class)).thenReturn(indexOperations);
        maintenance = new AggregatedNotificationIndexMaintenance(mongoTemplate);
    }

    @Test
    void afterPropertiesSet_shouldDropLegacyIndex_whenStartupRuns() {
        ReflectionTestUtils.setField(maintenance, "deleteMissingOrganisationIdAtStartup", false);

        maintenance.afterPropertiesSet();

        verify(indexOperations).dropIndex(AggregatedNotificationIndexMaintenance.LEGACY_STATUS_SORT_INDEX);
        verify(mongoTemplate, never()).remove(any(Query.class), eq(AggregatedNotification.class));
    }

    @Test
    void afterPropertiesSet_shouldContinue_whenLegacyIndexDropFails() {
        ReflectionTestUtils.setField(maintenance, "deleteMissingOrganisationIdAtStartup", false);
        doThrow(new RuntimeException("no such index"))
            .when(indexOperations)
            .dropIndex(AggregatedNotificationIndexMaintenance.LEGACY_STATUS_SORT_INDEX);

        maintenance.afterPropertiesSet();

        verify(indexOperations).dropIndex(AggregatedNotificationIndexMaintenance.LEGACY_STATUS_SORT_INDEX);
    }

    @Test
    void afterPropertiesSet_shouldSkipOrganisationCleanup_whenDisabled() {
        ReflectionTestUtils.setField(maintenance, "deleteMissingOrganisationIdAtStartup", false);

        maintenance.afterPropertiesSet();

        verify(mongoTemplate, never()).remove(any(Query.class), eq(AggregatedNotification.class));
    }

    @Test
    void afterPropertiesSet_shouldRemoveNotificationsWithoutOrganisationId_whenEnabled() {
        ReflectionTestUtils.setField(maintenance, "deleteMissingOrganisationIdAtStartup", true);
        when(mongoTemplate.remove(any(Query.class), eq(AggregatedNotification.class)))
            .thenReturn(DeleteResult.acknowledged(2));

        maintenance.afterPropertiesSet();

        verify(mongoTemplate).remove(any(Query.class), eq(AggregatedNotification.class));
    }

    @Test
    void afterPropertiesSet_shouldNotLogRemoval_whenNothingDeleted() {
        ReflectionTestUtils.setField(maintenance, "deleteMissingOrganisationIdAtStartup", true);
        when(mongoTemplate.remove(any(Query.class), eq(AggregatedNotification.class)))
            .thenReturn(DeleteResult.acknowledged(0));

        maintenance.afterPropertiesSet();

        verify(mongoTemplate).remove(any(Query.class), eq(AggregatedNotification.class));
    }
}