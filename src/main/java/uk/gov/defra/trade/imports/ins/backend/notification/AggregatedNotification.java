package uk.gov.defra.trade.imports.ins.backend.notification;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AggregatedNotification {

    @EqualsAndHashCode.Include
    @Id
    private String aggregateId;

    private long aggregateVersion;

    private String referenceNumber;
    private String status;
    private String originCountry;
    private String commodity;
    private Instant arrivalDate;
    private Instant lastUpdated;
}
