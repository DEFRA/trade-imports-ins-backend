package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import jakarta.annotation.Nullable;

/**
 * Collects the per-hop timings of one search so {@link AddressLookupClient} can return them.
 *
 * <p>A {@link ThreadLocal} because the token hops are not called by us: Spring Security runs them
 * from inside the lookup's own request interceptor, several frames below, with no argument we
 * could thread a collector through. They are synchronous and on the calling thread, so the
 * timings belong to the search that is running.
 *
 * <p>{@link #start()} clears anything left behind, and {@link #finish} removes the entry, so a
 * pooled request thread cannot carry one search's numbers into the next.
 */
final class LookupTimingsRecorder {

    private static final ThreadLocal<LookupTimingsRecorder> CURRENT = new ThreadLocal<>();

    private final long startedAt = System.currentTimeMillis();
    private Long stsMs;
    private Long entraMs;
    private long lookupMs;

    private LookupTimingsRecorder() {
    }

    static void start() {
        CURRENT.set(new LookupTimingsRecorder());
    }

    /** Records against the search in progress, if there is one; a no-op otherwise. */
    static void recordSts(long millis) {
        LookupTimingsRecorder current = CURRENT.get();
        if (current != null) {
            current.stsMs = millis;
        }
    }

    static void recordEntra(long millis) {
        LookupTimingsRecorder current = CURRENT.get();
        if (current != null) {
            current.entraMs = millis;
        }
    }

    static void recordLookup(long millis) {
        LookupTimingsRecorder current = CURRENT.get();
        if (current != null) {
            current.lookupMs = millis;
        }
    }

    /** Takes the timings and clears the thread, whatever happened to the search. */
    @Nullable
    static LookupTimings finish() {
        LookupTimingsRecorder current = CURRENT.get();
        CURRENT.remove();
        if (current == null) {
            return null;
        }
        return LookupTimings.of(
            current.stsMs,
            current.entraMs,
            current.lookupMs,
            System.currentTimeMillis() - current.startedAt);
    }
}
