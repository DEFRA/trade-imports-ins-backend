package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Wire shape of DEFRA {@code GET /addresses} ({@code addressLookup_21.yaml}
 * {@code Response_200_Results}). {@code header.totalResults} is a JSON string in the spec.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AddressLookupApiResponse(@Nullable Header header, @Nullable List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(@Nullable String totalResults) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(
        @Nullable String addressLine,
        @Nullable String buildingNumber,
        @Nullable String buildingName,
        @Nullable String subBuildingName,
        @Nullable String street,
        @Nullable String locality,
        @Nullable String town,
        @Nullable String postcode,
        @Nullable String country,
        @Nullable String uprn,
        @Nullable String match,
        @Nullable String matchDescription,
        @Nullable String language,
        @Nullable Integer xCoordinate,
        @Nullable Integer yCoordinate) {
    }
}
