package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Backend → frontend body for {@code GET /address-lookup}. Always HTTP 200 — a failed lookup
 * is {@link Outcome#FAILED}, so the spike page can render a message.
 */
@Schema(description = "Address lookup spike result. Always HTTP 200; failure is an outcome.")
public record AddressLookupResponse(
    @Schema(description = "What happened", requiredMode = RequiredMode.REQUIRED) Outcome outcome,
    @Schema(description = "The search that was run", requiredMode = RequiredMode.REQUIRED) Query query,
    @Schema(description = "Matching addresses; empty unless outcome is RESULTS",
        requiredMode = RequiredMode.REQUIRED)
    List<Address> results,
    @Schema(description = "totalResults from the lookup header, parsed from a string")
    @Nullable Integer totalResults,
    @Schema(description = "results.size()", requiredMode = RequiredMode.REQUIRED) int returnedResults,
    @Schema(description = "Why the lookup failed, when outcome is FAILED")
    @Nullable FailureReason failureReason) {

    public AddressLookupResponse {
        results = List.copyOf(results);
        returnedResults = results.size();
    }

    static AddressLookupResponse results(Query query, List<Address> addresses, @Nullable Integer totalResults) {
        List<Address> copy = List.copyOf(addresses);
        if (copy.isEmpty()) {
            return noResults(query);
        }
        return new AddressLookupResponse(Outcome.RESULTS, query, copy, totalResults, copy.size(), null);
    }

    static AddressLookupResponse noResults(Query query) {
        return new AddressLookupResponse(Outcome.NO_RESULTS, query, List.of(), 0, 0, null);
    }

    static AddressLookupResponse failed(Query query, FailureReason reason) {
        return new AddressLookupResponse(Outcome.FAILED, query, List.of(), null, 0, reason);
    }

    public record Query(
        @Schema(description = "How the term was searched") Mode mode,
        @Schema(description = "The search term") String term) {
    }

    public enum Outcome {
        RESULTS,
        NO_RESULTS,
        FAILED
    }

    public enum Mode {
        POSTCODE,
        FIND
    }

    public enum FailureReason {
        HTTP_401,
        HTTP_403,
        HTTP_500,
        HTTP_503,
        NON_JSON_200,
        TIMEOUT,
        TOKEN_FAILED,
        UNKNOWN
    }

    @Schema(description = "One address from DEFRA GET /addresses")
    public record Address(
        @Schema(description = "Full formatted address line") @Nullable String addressLine,
        @Schema(description = "Building number") @Nullable String buildingNumber,
        @Schema(description = "Building name, when the API returns one") @Nullable String buildingName,
        @Schema(description = "Sub-building name, when the API returns one") @Nullable String subBuildingName,
        @Schema(description = "Street") @Nullable String street,
        @Schema(description = "Locality") @Nullable String locality,
        @Schema(description = "Town") @Nullable String town,
        @Schema(description = "Postcode") @Nullable String postcode,
        @Schema(description = "Country name as returned by the lookup, not an ISO code") @Nullable String country,
        @Schema(description = "Unique Property Reference Number") @Nullable String uprn,
        @Schema(description = "Match code from the lookup") @Nullable String match,
        @Schema(description = "Human-readable match description") @Nullable String matchDescription,
        @Schema(description = "Language of the result") @Nullable String language,
        @Schema(description = "X coordinate") @Nullable Integer xCoordinate,
        @Schema(description = "Y coordinate") @Nullable Integer yCoordinate) {
    }
}
