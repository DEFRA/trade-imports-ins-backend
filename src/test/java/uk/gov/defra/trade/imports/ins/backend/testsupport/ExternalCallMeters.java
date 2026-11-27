package uk.gov.defra.trade.imports.ins.backend.testsupport;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import uk.gov.defra.trade.imports.ins.backend.metrics.Dependency;
import uk.gov.defra.trade.imports.ins.backend.metrics.ExternalCall;

public final class ExternalCallMeters {

  public static final ExternalCall SQS_RECEIVE_MESSAGE =
      new ExternalCall(Dependency.SQS, "receive-message");
  public static final ExternalCall SQS_DELETE_MESSAGE =
      new ExternalCall(Dependency.SQS, "delete-message");
  public static final ExternalCall MONGODB_INSERT = new ExternalCall(Dependency.MONGODB, "insert");
  public static final ExternalCall MONGODB_FIND = new ExternalCall(Dependency.MONGODB, "find");
  public static final ExternalCall MONGODB_UPDATE = new ExternalCall(Dependency.MONGODB, "update");

  private ExternalCallMeters() {}

  public static long durationSampleCount(MeterRegistry registry, ExternalCall call) {
    return duration(registry, call).map(Timer::count).orElse(0L);
  }

  public static double totalDurationMillis(MeterRegistry registry, ExternalCall call) {
    return duration(registry, call)
        .map(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
        .orElse(0.0);
  }

  public static long failureSampleCount(MeterRegistry registry, ExternalCall call) {
    return failure(registry, call).map(DistributionSummary::count).orElse(0L);
  }

  public static double failureTotal(MeterRegistry registry, ExternalCall call) {
    return failure(registry, call).map(DistributionSummary::totalAmount).orElse(0.0);
  }

  public static double availability(MeterRegistry registry, Dependency dependency) {
    return registry
        .get("external.dependency.available")
        .tag("dependency", dependency.tagValue())
        .gauge()
        .value();
  }

  private static Optional<Timer> duration(MeterRegistry registry, ExternalCall call) {
    return Optional.ofNullable(
        registry.find("external.call.duration").tags(tagsOf(call)).timer());
  }

  private static Optional<DistributionSummary> failure(MeterRegistry registry, ExternalCall call) {
    return Optional.ofNullable(
        registry.find("external.call.failure").tags(tagsOf(call)).summary());
  }

  private static String[] tagsOf(ExternalCall call) {
    return new String[] {
      "dependency", call.dependency().tagValue(), "operation", call.operation()
    };
  }
}
