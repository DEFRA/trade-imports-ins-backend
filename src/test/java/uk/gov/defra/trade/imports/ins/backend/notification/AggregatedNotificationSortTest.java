package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class AggregatedNotificationSortTest {

    @Test
    void toSort_shouldDefaultToArrivalDateDesc_whenSortParamIsNull() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort(null))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void toSort_shouldDefaultToArrivalDateDesc_whenSortParamIsBlank() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("  "))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void toSort_shouldDefaultToArrivalDateDesc_whenSortParamIsMalformed() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("arrivalDate"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void toSort_shouldDefaultToArrivalDateDesc_whenFieldIsUnrecognised() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("commodity,asc"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void toSort_shouldHonourAscendingDirection_forArrivalDate() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,asc"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "arrivalDate"));
    }

    @Test
    void toSort_shouldHonourDescendingDirection_forLastUpdated() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("lastUpdated,desc"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "lastUpdated"));
    }

    @Test
    void toSort_shouldHonourAscendingDirection_forLastUpdated() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("lastUpdated,asc"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "lastUpdated"));
    }

    @Test
    void toSort_shouldIgnoreCase_inDirection() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,ASC"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "arrivalDate"));
    }

    @Test
    void toSort_shouldDefaultToDescending_whenDirectionIsUnrecognised() {
        // When & Then
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,sideways"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }
}
