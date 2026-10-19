package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class AddressLookupMetrics {

    private static final String PREFIX = "addressLookup.";

    private final MeterRegistry meterRegistry;

    AddressLookupMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        registerEveryCounterAtZero();
    }

    private void registerEveryCounterAtZero() {
        List<String> names = new ArrayList<>(List.of("searches", "durationMs.lookup", "durationMs.total"));
        Arrays.stream(AddressLookupResponse.Mode.values()).map(AddressLookupMetrics::mode).forEach(names::add);
        Arrays.stream(AddressLookupResponse.Outcome.values()).map(AddressLookupMetrics::outcome).forEach(names::add);
        Arrays.stream(AddressLookupResponse.FailureReason.values()).map(AddressLookupMetrics::failure).forEach(names::add);
        names.forEach(name -> meterRegistry.counter(PREFIX + name));
    }

    void recordSearch(AddressLookupResponse response) {
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
        meterRegistry.counter(PREFIX + name).increment((double) millis);
    }
}
