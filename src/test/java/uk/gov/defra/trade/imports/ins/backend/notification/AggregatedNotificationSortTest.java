package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class AggregatedNotificationSortTest {

    @Test
    void nullSort_defaultsToArrivalDateDesc() {
        assertThat(AggregatedNotificationSort.toSort(null))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void blankSort_defaultsToArrivalDateDesc() {
        assertThat(AggregatedNotificationSort.toSort("  "))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void malformedSort_defaultsToArrivalDateDesc() {
        assertThat(AggregatedNotificationSort.toSort("arrivalDate"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void unrecognisedField_defaultsToArrivalDateDesc() {
        assertThat(AggregatedNotificationSort.toSort("commodity,asc"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }

    @Test
    void arrivalDateAsc_isHonoured() {
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,asc"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "arrivalDate"));
    }

    @Test
    void lastUpdatedDesc_isHonoured() {
        assertThat(AggregatedNotificationSort.toSort("lastUpdated,desc"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "lastUpdated"));
    }

    @Test
    void lastUpdatedAsc_isHonoured() {
        assertThat(AggregatedNotificationSort.toSort("lastUpdated,asc"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "lastUpdated"));
    }

    @Test
    void directionIsCaseInsensitive() {
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,ASC"))
            .isEqualTo(Sort.by(Sort.Direction.ASC, "arrivalDate"));
    }

    @Test
    void unrecognisedDirection_defaultsToDescending() {
        assertThat(AggregatedNotificationSort.toSort("arrivalDate,sideways"))
            .isEqualTo(Sort.by(Sort.Direction.DESC, "arrivalDate"));
    }
}
