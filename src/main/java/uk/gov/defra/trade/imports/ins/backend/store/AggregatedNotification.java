package uk.gov.defra.trade.imports.ins.backend.store;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@Data
@Builder
public class AggregatedNotification {

    @Id
    private String aggregateId;

    private long aggregateVersion;
    private String referenceNumber;
    private String status;
    private String originCountry;
    private String commodity;
    private String arrivalDate;
    private Instant lastUpdated;
}
