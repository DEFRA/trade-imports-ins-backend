package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The meters have to survive {@code EmfMetricsPublisher}, which publishes a meter's name and the
 * value of each of its measurements and nothing else. So every meter here must be a counter —
 * one measurement — and must carry its dimension in its name, or two meters collide in CloudWatch.
 */
class AddressLookupMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AddressLookupMetrics metrics = new AddressLookupMetrics(registry);

    @Test
    void constructor_shouldCreateEveryCounterAtZero_soTheFirstSearchIsNotLostToAScrape() {
        assertThat(registry.find("addressLookup.searches").counter().count()).isZero();
        assertThat(registry.find("addressLookup.mode.find").counter().count()).isZero();
        assertThat(registry.find("addressLookup.outcome.no_results").counter().count()).isZero();
        assertThat(registry.find("addressLookup.failure.TIMEOUT").counter().count()).isZero();
        assertThat(registry.find("addressLookup.durationMs.lookup").counter().count()).isZero();
    }

    @Test
    void record_shouldCountTheSearchByModeAndOutcome() {
        metrics.record(results(AddressLookupResponse.Mode.POSTCODE, cachedTimings()));

        assertThat(counter("addressLookup.searches")).isEqualTo(1);
        assertThat(counter("addressLookup.mode.postcode")).isEqualTo(1);
        assertThat(counter("addressLookup.outcome.results")).isEqualTo(1);
    }

    @Test
    void record_shouldNameTheFailureReason_soEachIsItsOwnMetric() {
        metrics.record(AddressLookupResponse
            .failed(query(AddressLookupResponse.Mode.POSTCODE), AddressLookupResponse.FailureReason.HTTP_503)
            .withTimings(cachedTimings()));

        assertThat(counter("addressLookup.outcome.failed")).isEqualTo(1);
        assertThat(counter("addressLookup.failure.HTTP_503")).isEqualTo(1);
    }

    @Test
    void record_shouldAccumulateDurations_soTheDashboardCanDivideForAMean() {
        metrics.record(results(AddressLookupResponse.Mode.POSTCODE, cachedTimings()));
        metrics.record(results(AddressLookupResponse.Mode.POSTCODE, cachedTimings()));

        assertThat(counter("addressLookup.searches")).isEqualTo(2);
        assertThat(counter("addressLookup.durationMs.lookup")).isEqualTo(1400);
        assertThat(counter("addressLookup.durationMs.total")).isEqualTo(1410);
    }

    @Test
    void everyMeterShouldBeASingleMeasurement_orTheEmfPublisherWouldCollideThem() {
        metrics.record(results(AddressLookupResponse.Mode.POSTCODE, LookupTimings.of(1000L, 800L, 2000L, 2100L)));

        assertThat(registry.getMeters())
            .allSatisfy(meter -> assertThat(meter.measure()).hasSize(1));
    }

    @Test
    void record_shouldNotFail_whenThereAreNoTimings() {
        metrics.record(AddressLookupResponse.noResults(query(AddressLookupResponse.Mode.FIND)));

        assertThat(counter("addressLookup.outcome.no_results")).isEqualTo(1);
    }

    private double counter(String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    private static LookupTimings cachedTimings() {
        return LookupTimings.of(null, null, 700L, 705L);
    }

    private static AddressLookupResponse.Query query(AddressLookupResponse.Mode mode) {
        return new AddressLookupResponse.Query(mode, "SW1A 1AA");
    }

    private static AddressLookupResponse results(AddressLookupResponse.Mode mode, LookupTimings timings) {
        return AddressLookupResponse
            .results(query(mode), List.of(new AddressLookupResponse.Address(
                "BUCKINGHAM PALACE, LONDON, SW1A 1AA", null, null, "BUCKINGHAM PALACE", null, null,
                "LONDON", "SW1A 1AA", "ENGLAND", "100023336901", "1", "EXACT", "EN", null, null)), 1)
            .withTimings(timings);
    }
}
