package uk.gov.defra.trade.imports.ins.backend.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class DependencyAvailabilityMetrics {

  DependencyAvailabilityMetrics(
      MeterRegistry meterRegistry,
      MongoAvailability mongoAvailability,
      LastCallOutcomes lastCallOutcomes) {
    for (Dependency dependency : Dependency.values()) {
      Supplier<Availability> availability =
          switch (dependency.availabilitySource()) {
            case MONGO_SERVER_CHECKS -> mongoAvailability::state;
            case LAST_CALL -> () -> lastCallOutcomes.lastCallAvailability(dependency);
          };
      register(meterRegistry, dependency, availability);
    }
  }

  private static void register(
      MeterRegistry meterRegistry, Dependency dependency, Supplier<Availability> availability) {
    Gauge.builder("external.dependency.available", () -> gaugeValueOf(availability.get()))
        .tag(ExternalCallRecorder.DEPENDENCY_TAG, dependency.tagValue())
        .register(meterRegistry);
  }

  private static double gaugeValueOf(Availability availability) {
    return switch (availability) {
      case AVAILABLE -> 1;
      case UNAVAILABLE -> 0;
      case UNKNOWN -> Double.NaN;
    };
  }
}
