package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Publishes the search's own numbers as Micrometer meters, so they reach CloudWatch through
 * {@code EmfMetricsPublisher} and can be put on a Grafana dashboard (CDP monitoring guide:
 * dashboards live in {@code Playground/trade-imports-ins-backend-monitoring}, CloudWatch
 * datasource, namespace = the service name).
 *
 * <p><strong>Counters only, with the dimension in the name.</strong> That publisher writes name
 * and value and nothing else: Micrometer tags are dropped, and every measurement of a meter is
 * published under the meter's name. A {@code Timer} would therefore publish its count, total and
 * max as three values all called the same thing, and two meters differing only by a tag would
 * collide. So each thing worth counting gets its own name.
 *
 * <p>Durations are published as running totals rather than averages, because a counter is the
 * one shape that survives being summed across a minute's flush and across instances. The
 * dashboard divides: mean lookup time is {@code addressLookup.durationMs.lookup} over
 * {@code addressLookup.searches}. That gives a mean and not a percentile — the publisher cannot
 * carry a distribution, and changing it is a shared concern well outside this spike.
 */
class AddressLookupMetrics {

    private static final String PREFIX = "addressLookup.";

    private final MeterRegistry meterRegistry;

    AddressLookupMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        registerAtZero();
    }

    /**
     * A counter created by its first increment is first scraped already above zero, and a
     * Prometheus {@code increase()} cannot see that first rise. Creating every counter up front
     * lets the local dashboard count the first search too. CloudWatch is unaffected: the publisher
     * sends a 0 for the first minute, then removes the counters as it does every minute.
     */
    private void registerAtZero() {
        List<String> names = new ArrayList<>(List.of("searches", "durationMs.lookup", "durationMs.total"));
        Arrays.stream(AddressLookupResponse.Mode.values()).map(AddressLookupMetrics::mode).forEach(names::add);
        Arrays.stream(AddressLookupResponse.Outcome.values()).map(AddressLookupMetrics::outcome).forEach(names::add);
        Arrays.stream(AddressLookupResponse.FailureReason.values()).map(AddressLookupMetrics::failure).forEach(names::add);
        names.forEach(name -> meterRegistry.counter(PREFIX + name));
    }

    void record(AddressLookupResponse response) {
        count("searches");
        count(mode(response.query().mode()));
        count(outcome(response.outcome()));

        if (response.failureReason() != null) {
            count(failure(response.failureReason()));
        }

        LookupTimings timings = response.timings();
        if (timings == null) {
            return;
        }

        add("durationMs.lookup", timings.lookupMs());
        add("durationMs.total", timings.totalMs());
    }

    private static String mode(AddressLookupResponse.Mode mode) {
        return "mode." + mode.name().toLowerCase();
    }

    private static String outcome(AddressLookupResponse.Outcome outcome) {
        return "outcome." + outcome.name().toLowerCase();
    }

    private static String failure(AddressLookupResponse.FailureReason reason) {
        return "failure." + reason.name();
    }

    private void count(String name) {
        meterRegistry.counter(PREFIX + name).increment();
    }

    private void add(String name, long millis) {
        meterRegistry.counter(PREFIX + name).increment(millis);
    }
}
