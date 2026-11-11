package uk.gov.defra.trade.imports.ins.backend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatedNotificationOrganisationCleanup implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query missingOwner = Query.query(
            new Criteria().orOperator(
                Criteria.where("organisationId").exists(false),
                Criteria.where("organisationId").isNull(),
                Criteria.where("organisationId").is("")));
        long removed = mongoTemplate.remove(missingOwner, AggregatedNotification.class).getDeletedCount();
        if (removed > 0) {
            log.info("Removed {} aggregated notifications with no organisationId at startup", removed);
        }
    }
}
