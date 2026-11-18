package uk.gov.defra.trade.imports.ins.backend.notification;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@CompoundIndex(
    name = AggregatedNotification.ORGANISATION_STATUS_SORT_INDEX,
    def = "{'organisationId': 1, 'status': 1, 'arrivalDate': -1, 'lastUpdated': -1}")
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AggregatedNotification {

    public static final String ORGANISATION_ID_FIELD = "organisationId";
    public static final String ORGANISATION_STATUS_SORT_INDEX = "organisation_status_sort_fields";

    @EqualsAndHashCode.Include
    @Id
    private String aggregateId;

    private long aggregateVersion;

    @Indexed
    private String referenceNumber;

    private String organisationId;
    private String status;
    private String originCountry;
    private String originCountrySubdivisionCode;
    private String commodity;
    private LocalDate arrivalDate;
    private Instant lastUpdated;
}
