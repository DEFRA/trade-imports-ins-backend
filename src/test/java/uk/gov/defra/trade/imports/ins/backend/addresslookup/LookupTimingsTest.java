package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LookupTimingsTest {

    @Test
    void of_shouldTakeTheTokenHopsOffTheExchange_soLookupMsIsTheGatewaysOwnTime() {
        // A cold search: Spring Security runs both token hops inside the lookup exchange, so the
        // 2000ms measured around it contains them.
        LookupTimings timings = LookupTimings.of(1000L, 800L, 2000L, 2100L);

        assertThat(timings.lookupMs()).isEqualTo(200);
        assertThat(timings.totalMs()).isEqualTo(2100);
    }

    @Test
    void of_shouldReportMintedWhenEitherTokenHopRan() {
        assertThat(LookupTimings.of(1000L, 800L, 2000L, 2100L).tokenSource())
            .isEqualTo(LookupTimings.TokenSource.MINTED);
    }

    @Test
    void of_shouldReportCachedWhenNeitherTokenHopRan() {
        LookupTimings timings = LookupTimings.of(null, null, 700L, 705L);

        assertThat(timings.tokenSource()).isEqualTo(LookupTimings.TokenSource.CACHED);
        assertThat(timings.lookupMs()).isEqualTo(700);
        assertThat(timings.stsMs()).isNull();
        assertThat(timings.entraMs()).isNull();
    }

    @Test
    void of_shouldNotReportANegativeLookup_whenTheHopsOverlapTheMeasurement() {
        // Clock granularity can make the parts add to more than the whole; a negative duration
        // on the page would look like a bug in the page rather than in the measurement.
        LookupTimings timings = LookupTimings.of(500L, 500L, 900L, 1000L);

        assertThat(timings.lookupMs()).isZero();
    }
}
