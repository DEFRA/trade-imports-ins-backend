package uk.gov.defra.trade.imports.ins.backend.notification;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AggregatedNotificationRepository extends MongoRepository<AggregatedNotification, String> {

    Page<AggregatedNotification> findAllByStatusNot(String status, Pageable pageable);

    Optional<AggregatedNotification> findByReferenceNumberAndStatusNot(String referenceNumber, String status);
}
