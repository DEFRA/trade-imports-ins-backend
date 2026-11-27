package uk.gov.defra.trade.imports.ins.backend.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.assertOneLine;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.CapturedLogs.loggedBy;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.SQS_RECEIVE_MESSAGE;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.durationSampleCount;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.failureTotal;
import static uk.gov.defra.trade.imports.ins.backend.testsupport.ExternalCallMeters.totalDurationMillis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.services.sqs.model.SqsException;

class AwsCallRecorderTest {

  private static final SqsException CAUSE =
      (SqsException) SqsException.builder().message("The queue does not exist").build();

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final LastCallOutcomes lastCallOutcomes = new LastCallOutcomes();
  private final AtomicLong nanoTime = new AtomicLong();
  private final AwsCallRecorder sqsRecorder =
      new AwsCallRecorder(
          new ExternalCallRecorder(meterRegistry, lastCallOutcomes, nanoTime::get),
          Dependency.SQS,
          Set.of("receive-message", "delete-message-batch"));

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "ReceiveMessage, receive-message",
    "DeleteMessageBatch, delete-message-batch",
    "PurgeQueue, other"
  })
  void callOf_shouldBeTheKnownActionInKebabCase_orOtherOutsideTheKnownSet(
      String action, String operation) {
    assertThat(sqsRecorder.callOf(attributesFor(action)))
        .isEqualTo(new ExternalCall(Dependency.SQS, operation));
  }

  @Test
  void callOf_shouldBeOther_whenTheCallHasNoAction() {
    assertThat(sqsRecorder.callOf(new ExecutionAttributes()).operation()).isEqualTo("other");
  }

  @Test
  void afterExecution_shouldRecordTheTimeSinceTheCallBeganAsASuccessOfItsDependency() {
    ExecutionAttributes attributes = attributesFor("ReceiveMessage");

    sqsRecorder.beforeExecution(null, attributes);
    nanoTime.addAndGet(Duration.ofMillis(40).toNanos());
    sqsRecorder.afterExecution(null, attributes);

    assertThat(durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(40);
    assertThat(failureTotal(meterRegistry, SQS_RECEIVE_MESSAGE)).isZero();
    assertThat(lastCallOutcomes.lastCallAvailability(Dependency.SQS))
        .isEqualTo(Availability.AVAILABLE);
  }

  @Test
  void onExecutionFailure_shouldRecordTheTimeSinceTheCallBeganAsAFailureOfItsDependency() {
    ExecutionAttributes attributes = attributesFor("ReceiveMessage");

    sqsRecorder.beforeExecution(null, attributes);
    nanoTime.addAndGet(Duration.ofMillis(25).toNanos());
    sqsRecorder.onExecutionFailure(failedWith(CAUSE), attributes);

    assertThat(durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(totalDurationMillis(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(25);
    assertThat(failureTotal(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(lastCallOutcomes.lastCallAvailability(Dependency.SQS))
        .isEqualTo(Availability.UNAVAILABLE);
  }

  @Test
  void onExecutionFailure_shouldRecordAFailureWithNoDurationAndSaySo_whenTheCallNeverBegan() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> sqsRecorder.onExecutionFailure(failedWith(CAUSE), attributesFor("ReceiveMessage")));

    assertThat(failureTotal(meterRegistry, SQS_RECEIVE_MESSAGE)).isEqualTo(1);
    assertThat(durationSampleCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isZero();
    ILoggingEvent line =
        assertOneLine(
            lines,
            Level.WARN,
            "External call sqs/receive-message failure with no start time recorded");
    assertThat(keyValues(line))
        .containsEntry("event.action", "sqs/receive-message")
        .doesNotContainKey("event.duration");
  }

  private static ExecutionAttributes attributesFor(String action) {
    ExecutionAttributes attributes = new ExecutionAttributes();
    attributes.putAttribute(SdkExecutionAttribute.OPERATION_NAME, action);
    return attributes;
  }

  private static Context.FailedExecution failedWith(Throwable cause) {
    Context.FailedExecution context = mock(Context.FailedExecution.class);
    when(context.exception()).thenReturn(cause);
    return context;
  }
}
