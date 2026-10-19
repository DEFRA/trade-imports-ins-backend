package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

/**
 * How long each hop of one search took.
 *
 * <p>{@code stsMs} and {@code entraMs} are null on a warm search. The token hops only run when
 * the cached Entra token has expired.
 */
@Schema(description = "Elapsed time per hop, for the search just run")
public record LookupTimings(
    @Schema(description = "AWS STS GetWebIdentityToken, or null when the cached token was reused")
    @Nullable Long stsMs,
    @Schema(description = "Entra token endpoint, or null when the cached token was reused")
    @Nullable Long entraMs,
    @Schema(description = "The gateway lookup call itself") long lookupMs,
    @Schema(description = "Everything the backend did, including any token hops") long totalMs,
    @Schema(description = "Whether this search paid for a new token") TokenSource tokenSource) {

    public enum TokenSource {
        MINTED,
        CACHED
    }

    /**
     * @param exchangeMs the whole lookup exchange, including any token hops run inside it;
     *     {@code lookupMs} is this less {@code stsMs} and {@code entraMs}
     */
    static LookupTimings of(@Nullable Long stsMs, @Nullable Long entraMs, long exchangeMs, long totalMs) {
        long tokenMs = (stsMs == null ? 0 : stsMs) + (entraMs == null ? 0 : entraMs);
        return new LookupTimings(
            stsMs, entraMs, Math.max(0, exchangeMs - tokenMs), totalMs,
            stsMs == null && entraMs == null ? TokenSource.CACHED : TokenSource.MINTED);
    }
}
