package uk.gov.defra.trade.imports.ins.backend.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.assertOneLine;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.loggedBy;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.MONGODB_FIND;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.SQS_RECEIVE_MESSAGE;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.durationSampleCount;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.failureSampleCount;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.failureTotal;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.totalDurationMillis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ExternalCallRecorderTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final LastCallOutcomes lastCallOutcomes = new LastCallOutcomes();
  private final AtomicLong nanoTime = new AtomicLong();
  private final ExternalCallRecorder recorder =
      new ExternalCallRecorder(meterRegistry, lastCallOutcomes, nanoTime::get);

  @Test
  void recordSuccess_shouldRecordTheDurationAZeroFailureAndTheDependencyAsAvailable() {
    recorder.recordSuccess(SQS_RECEIVE_MESSAGE, Duration.ofMillis(7));

    assertThat(durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(7);
    assertThat(failureSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, SQS_RECEIVE_MESSAGE)).isZero();
    assertThat(lastCallOutcomes.lastCallAvailability(Dependency.SQS))
        .isEqualTo(Availability.AVAILABLE);
  }

  @Test
  void recordFailure_shouldRecordTheDurationAFailureOfOneAndTheDependencyAsUnavailable() {
    recorder.recordFailure(MONGODB_FIND, Duration.ofMillis(3), new IllegalStateException());

    assertThat(durationSampleCount(meterRegistry, MONGODB_FIND)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, MONGODB_FIND)).isEqualTo(3);
    assertThat(failureSampleCount(meterRegistry, MONGODB_FIND)).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, MONGODB_FIND)).isEqualTo(1);
    assertThat(lastCallOutcomes.lastCallAvailability(Dependency.MONGODB))
        .isEqualTo(Availability.UNAVAILABLE);
  }

  @Test
  void recordSuccess_shouldLogOneInfoLineWithTheEventFieldsAndANanosecondDuration() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class, () -> recorder.recordSuccess(SQS_RECEIVE_MESSAGE, Duration.ofMillis(2)));

    ILoggingEvent line =
        assertOneLine(lines, Level.INFO, "External call sqs/receive-message success in 2 ms");
    assertThat(keyValues(line))
        .containsEntry("event.category", "external-call")
        .containsEntry("event.action", "sqs/receive-message")
        .containsEntry("event.outcome", "success")
        .containsEntry("event.duration", 2_000_000L);
  }

  @Test
  void recordFailure_shouldLogOneWarnLineWithTheErrorTypeAndMessageButNoStackTrace() {
    IllegalStateException cause = new IllegalStateException("The collection does not exist");

    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> recorder.recordFailure(MONGODB_FIND, Duration.ofMillis(4), cause));

    ILoggingEvent line =
        assertOneLine(lines, Level.WARN, "External call mongodb/find failure in 4 ms");
    assertThat(keyValues(line))
        .containsEntry("event.action", "mongodb/find")
        .containsEntry("event.outcome", "failure")
        .containsEntry("error.type", "java.lang.IllegalStateException")
        .containsEntry("error.message", "The collection does not exist");
    assertThat(line.getThrowableProxy()).isNull();
  }

  @Test
  void elapsedSince_shouldBeTheTimeSinceCallStartedAt() {
    long startedAt = recorder.callStartedAt();
    nanoTime.addAndGet(Duration.ofMillis(12).toNanos());

    assertThat(recorder.elapsedSince(startedAt)).isEqualTo(Duration.ofMillis(12));
  }
}
