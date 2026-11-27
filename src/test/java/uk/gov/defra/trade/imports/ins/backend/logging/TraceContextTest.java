package uk.gov.defra.trade.imports.ins.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static uk.gov.defra.trade.imports.ins.backend.logging.TraceContext.MDC_TRACE_ID;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceContextTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void runWithTraceId_shouldSetTheTraceIdWhileTheWorkRuns() {
    AtomicReference<String> traceIdSeenByTheWork = new AtomicReference<>();

    TraceContext.runWithTraceId("trace-1", () -> traceIdSeenByTheWork.set(MDC.get(MDC_TRACE_ID)));

    assertThat(traceIdSeenByTheWork).hasValue("trace-1");
  }

  @Test
  void runWithTraceId_shouldRemoveTheTraceIdAfterwards_whenThereWasNone() {
    TraceContext.runWithTraceId("trace-1", () -> {});

    assertThat(MDC.get(MDC_TRACE_ID)).isNull();
  }

  @Test
  void runWithTraceId_shouldPutBackTheEnclosingTraceIdAndLeaveOtherKeys_whenTheWorkThrows() {
    MDC.put(MDC_TRACE_ID, "enclosing-trace");
    MDC.put("url.full", "http://localhost/notifications");

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                TraceContext.runWithTraceId(
                    "trace-1",
                    () -> {
                      throw new IllegalStateException("work failed");
                    }));

    assertThat(MDC.get(MDC_TRACE_ID)).isEqualTo("enclosing-trace");
    assertThat(MDC.get("url.full")).isEqualTo("http://localhost/notifications");
  }
}
