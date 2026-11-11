package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AggregatedNotificationOrganisationCleanupTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private AggregatedNotificationOrganisationCleanup cleanup;

    @BeforeEach
    void setup() {
        cleanup = new AggregatedNotificationOrganisationCleanup(mongoTemplate);
    }

    @Test
    void run_shouldDeleteNotificationsWithNoOrganisationId() {
        when(mongoTemplate.remove(any(Query.class), eq(AggregatedNotification.class)))
            .thenReturn(DeleteResult.acknowledged(0));

        List<ILoggingEvent> events = runCleanup();

        assertThat(removedQuery()).isEqualTo(notificationsWithNoOrganisationId());
        assertThat(events).isEmpty();
    }

    @Test
    void run_shouldLogHowManyNotificationsItRemoved_whenSomeHaveNoOrganisationId() {
        when(mongoTemplate.remove(any(Query.class), eq(AggregatedNotification.class)))
            .thenReturn(DeleteResult.acknowledged(3));

        List<ILoggingEvent> events = runCleanup();

        assertThat(events).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
            .isEqualTo("Removed 3 aggregated notifications with no organisationId at startup"));
    }

    private List<ILoggingEvent> runCleanup() {
        Logger logger = (Logger) LoggerFactory.getLogger(AggregatedNotificationOrganisationCleanup.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            cleanup.run(new DefaultApplicationArguments());
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private Document removedQuery() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(queryCaptor.capture(), eq(AggregatedNotification.class));
        return queryCaptor.getValue().getQueryObject();
    }

    private static Document notificationsWithNoOrganisationId() {
        return new Document("$or", List.of(
            new Document("organisationId", new Document("$exists", false)),
            new Document("organisationId", null),
            new Document("organisationId", "")));
    }
}
