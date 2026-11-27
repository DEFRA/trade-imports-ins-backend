package uk.gov.defra.trade.imports.ins.backend.logging;

/** The MDC keys that carry a unit of work's trace context into every log line. */
public final class TraceContext {

  public static final String MDC_TRACE_ID = "trace.id";

  private TraceContext() {}
}
