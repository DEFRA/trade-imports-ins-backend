package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;

class AggregatedNotificationControllerIT extends IntegrationBase {

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        mongoTemplate.dropCollection(AggregatedNotification.class);
    }

    @Test
    void findAll_defaultSort_excludesDeleted_ordersByArrivalDateDesc() throws Exception {
        // Given
        seed("agg-earlier", "GBN-AG-26-001", "SUBMITTED", "GB", "Domestic", "2026-08-01T00:00:00Z");
        seed("agg-later", "GBN-AG-26-002", "DRAFT", "FR", "Live cattle", "2026-08-20T00:00:00Z");
        seed("agg-deleted", "GBN-AG-26-003", "DELETED", "GB", "Domestic", "2026-08-25T00:00:00Z");

        // When / Then
        mockMvc.perform(get("/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].referenceNumber").value("GBN-AG-26-002"))
            .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
            .andExpect(jsonPath("$.content[0].originCountry").value("FR"))
            .andExpect(jsonPath("$.content[0].commodity").value("Live cattle"))
            .andExpect(jsonPath("$.content[0].arrivalDate").value("2026-08-20T00:00:00Z"))
            .andExpect(jsonPath("$.content[1].referenceNumber").value("GBN-AG-26-001"))
            .andExpect(jsonPath("$.content[1].status").value("SUBMITTED"))
            .andExpect(jsonPath("$.content[1].originCountry").value("GB"))
            .andExpect(jsonPath("$.content[1].commodity").value("Domestic"))
            .andExpect(jsonPath("$.content[1].arrivalDate").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void findAll_sortAscending_isHonoured() throws Exception {
        // Given
        seed("agg-earlier", "GBN-AG-26-001", "SUBMITTED", "GB", "Domestic", "2026-08-01T00:00:00Z");
        seed("agg-later", "GBN-AG-26-002", "DRAFT", "FR", "Live cattle", "2026-08-20T00:00:00Z");

        // When / Then
        mockMvc.perform(get("/notifications").param("sort", "arrivalDate,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].referenceNumber").value("GBN-AG-26-001"))
            .andExpect(jsonPath("$.content[1].referenceNumber").value("GBN-AG-26-002"));
    }

    @Test
    void findAll_referenceNumber_exactMatch_returnsOnlyThatNotification() throws Exception {
        // Given
        seed("agg-1", "GBN-AG-26-001", "SUBMITTED", "GB", "Domestic", "2026-08-01T00:00:00Z");
        seed("agg-2", "GBN-AG-26-002", "DRAFT", "FR", "Live cattle", "2026-08-20T00:00:00Z");

        // When / Then
        mockMvc.perform(get("/notifications").param("referenceNumber", "GBN-AG-26-002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].referenceNumber").value("GBN-AG-26-002"))
            .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
            .andExpect(jsonPath("$.content[0].originCountry").value("FR"))
            .andExpect(jsonPath("$.content[0].commodity").value("Live cattle"))
            .andExpect(jsonPath("$.content[0].arrivalDate").value("2026-08-20T00:00:00Z"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void findAll_referenceNumber_noMatch_returnsEmptyContent_not404() throws Exception {
        // Given
        seed("agg-1", "GBN-AG-26-001", "SUBMITTED", "GB", "Domestic", "2026-08-01T00:00:00Z");

        // When / Then
        mockMvc.perform(get("/notifications").param("referenceNumber", "GBN-AG-26-999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void findAll_referenceNumber_matchingDeletedNotification_isExcluded() throws Exception {
        // Given
        seed("agg-deleted", "GBN-AG-26-003", "DELETED", "GB", "Domestic", "2026-08-25T00:00:00Z");

        // When / Then
        mockMvc.perform(get("/notifications").param("referenceNumber", "GBN-AG-26-003"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void findAll_emptyStore_returnsEmptyContent() throws Exception {
        // Given / When / Then — nothing seeded
        mockMvc.perform(get("/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void findAll_pageIsZero_returnsBadRequest() throws Exception {
        // When / Then — @Min(1) on page is violated; must map to 400, not a 500
        mockMvc.perform(get("/notifications").param("page", "0"))
            .andExpect(status().isBadRequest());
    }

    private void seed(String aggregateId, String referenceNumber, String status, String originCountry,
            String commodity, String arrivalDate) {
        mongoTemplate.save(AggregatedNotification.builder()
            .aggregateId(aggregateId)
            .aggregateVersion(1L)
            .referenceNumber(referenceNumber)
            .status(status)
            .originCountry(originCountry)
            .commodity(commodity)
            .arrivalDate(Instant.parse(arrivalDate))
            .lastUpdated(Instant.now())
            .build());
    }
}
