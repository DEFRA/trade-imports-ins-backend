package uk.gov.defra.trade.imports.ins.backend.logging;

import org.slf4j.MDC;

/** The MDC keys that carry a unit of work's trace context into every log line. */
public final class TraceContext {

  public static final String MDC_TRACE_ID = "trace.id";

  private TraceContext() {}

  /**
   * Runs {@code work} with {@code traceId} as the MDC trace id, then puts back the trace id that
   * was there before, or removes it when there was none. Other MDC keys are left as they are.
   */
  public static void runWithTraceId(String traceId, Runnable work) {
    String enclosingTraceId = MDC.get(MDC_TRACE_ID);
    MDC.put(MDC_TRACE_ID, traceId);
    try {
      work.run();
    } finally {
      if (enclosingTraceId == null) {
        MDC.remove(MDC_TRACE_ID);
      } else {
        MDC.put(MDC_TRACE_ID, enclosingTraceId);
      }
    }
  }
}
