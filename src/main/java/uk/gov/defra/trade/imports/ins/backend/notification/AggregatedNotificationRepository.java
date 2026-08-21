package uk.gov.defra.trade.imports.ins.backend.notification;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AggregatedNotificationRepository extends MongoRepository<AggregatedNotification, String> {
}
