package uk.gov.defra.trade.imports.ins.backend.notification;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@CompoundIndex(name = "status_sort_fields", def = "{'status': 1, 'arrivalDate': -1, 'lastUpdated': -1}")
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AggregatedNotification {

    @EqualsAndHashCode.Include
    @Id
    private String aggregateId;

    private long aggregateVersion;

    @Indexed
    private String referenceNumber;
    private String status;
    private String originCountry;
    private String commodity;
    private Instant arrivalDate;
    private Instant lastUpdated;
}
